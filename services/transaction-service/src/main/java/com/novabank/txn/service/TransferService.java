package com.novabank.txn.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novabank.common.exception.BusinessException;
import com.novabank.common.kafka.Topics;
import com.novabank.common.money.Money;
import com.novabank.txn.domain.OutboxEvent;
import com.novabank.txn.domain.ScheduledPayment;
import com.novabank.txn.domain.Transfer;
import com.novabank.txn.repo.OutboxEventRepository;
import com.novabank.txn.repo.ScheduledPaymentRepository;
import com.novabank.txn.repo.TransferRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TransferService {
    private static final Set<String> CHANNELS = Set.of("NEFT", "IMPS", "RTGS", "UPI", "INTERNAL");
    private final TransferRepository transfers;
    private final ScheduledPaymentRepository scheduled;
    private final OutboxEventRepository outbox;
    private final ObjectMapper mapper;
    private final StringRedisTemplate redis;
    private volatile RestClient rest;
    private final String accountUrl;
    private final String ledgerUrl;
    private final String fraudUrl;
    private final String internalToken;

    public TransferService(TransferRepository transfers, ScheduledPaymentRepository scheduled, OutboxEventRepository outbox,
                           ObjectMapper mapper, StringRedisTemplate redis,
                           @Value("${novabank.account-service-url}") String accountUrl,
                           @Value("${novabank.ledger-service-url}") String ledgerUrl,
                           @Value("${novabank.fraud-service-url}") String fraudUrl,
                           @Value("${novabank.internal-token}") String internalToken) {
        this.transfers = transfers;
        this.scheduled = scheduled;
        this.outbox = outbox;
        this.mapper = mapper;
        this.redis = redis;
        this.accountUrl = accountUrl;
        this.ledgerUrl = ledgerUrl;
        this.fraudUrl = fraudUrl;
        this.internalToken = internalToken;
    }

    public record TransferRequest(@NotNull Long debitAccountId, @NotNull Long creditAccountId,
                                  @NotNull @Positive BigDecimal amount, String channel, String narration) {}
    public record TransferView(Long id, String publicId, Long debitAccountId, Long creditAccountId, BigDecimal amount,
                               String channel, String status, String narration, BigDecimal fraudScore, Instant createdAt) {
        static TransferView of(Transfer t) {
            return new TransferView(t.getId(), t.getPublicId().toString(), t.getDebitAccountId(), t.getCreditAccountId(),
                    t.getAmount(), t.getChannel(), t.getStatus(), t.getNarration(), t.getFraudScore(), t.getCreatedAt());
        }
    }

    @Transactional(noRollbackFor = RestClientException.class)
    public TransferView transfer(Long userId, String idempotencyKey, TransferRequest req) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_REQUIRED", "Idempotency-Key header required");
        }
        var existing = transfers.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Transfer t = existing.get();
            String channel = req.channel() == null ? "IMPS" : req.channel().toUpperCase();
            if (!t.getUserId().equals(userId) || !t.getDebitAccountId().equals(req.debitAccountId())
                    || !t.getCreditAccountId().equals(req.creditAccountId())
                    || t.getAmount().compareTo(Money.normalize(req.amount())) != 0 || !t.getChannel().equals(channel)) {
                throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key was already used for a different transfer");
            }
            return "PENDING".equals(t.getStatus()) ? continueExecution(t) : TransferView.of(t);
        }
        return execute(userId, idempotencyKey, req);
    }

    private TransferView execute(Long userId, String key, TransferRequest req) {
        BigDecimal amount = Money.normalize(req.amount());
        if (!Money.isPositive(amount)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Amount must be positive");
        }
        String channel = req.channel() == null ? "IMPS" : req.channel().toUpperCase();
        if (!CHANNELS.contains(channel)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CHANNEL", "Simulated channels: NEFT, IMPS, RTGS, UPI, INTERNAL");
        }
        Transfer t = new Transfer();
        t.setIdempotencyKey(key);
        t.setUserId(userId);
        t.setDebitAccountId(req.debitAccountId());
        t.setCreditAccountId(req.creditAccountId());
        t.setAmount(amount);
        t.setChannel(channel);
        t.setNarration(req.narration());
        t.setStatus("PENDING");
        transfers.save(t);

        return continueExecution(t);
    }

    private TransferView continueExecution(Transfer t) {
        Long userId = t.getUserId();
        BigDecimal amount = t.getAmount();
        String channel = t.getChannel();

        BigDecimal score = scoreFraud(t);
        t.setFraudScore(score);
        if (score.compareTo(new BigDecimal("0.85")) >= 0) {
            t.setStatus("HELD_FRAUD");
            enqueue(Topics.FRAUD_SCORED, Map.of("transferId", t.getId(), "score", score, "status", t.getStatus()));
            return TransferView.of(t);
        }

        applyBalances(t);
        postLedger(t);
        t.setStatus("COMPLETED");
        t.setCompletedAt(Instant.now());
        enqueue(Topics.TRANSFER_COMPLETED, Map.of(
                "transferId", t.getId(), "userId", userId, "amount", amount, "channel", channel,
                "debitAccountId", t.getDebitAccountId(), "creditAccountId", t.getCreditAccountId()));
        enqueue(Topics.NOTIFICATION, Map.of("userId", userId, "title", "Transfer completed",
                "body", "INR " + amount + " sent via simulated " + channel));
        return TransferView.of(t);
    }

    @CircuitBreaker(name = "accountService")
    @Retry(name = "accountService")
    protected void applyBalances(Transfer t) {
        rest().post().uri(accountUrl + "/internal/accounts/apply-transfer")
                .header("X-Internal-Token", internalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("transferId", t.getId(), "userId", t.getUserId(), "debitAccountId", t.getDebitAccountId(), "creditAccountId", t.getCreditAccountId(), "amount", t.getAmount()))
                .retrieve()
                .toBodilessEntity();
    }

    private void postLedger(Transfer t) {
        rest().post().uri(ledgerUrl + "/internal/ledger/entries")
                .header("X-Internal-Token", internalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("transferId", t.getId(), "description", "SIM-" + t.getChannel(),
                        "debitAccountId", t.getDebitAccountId(), "creditAccountId", t.getCreditAccountId(), "amount", t.getAmount()))
                .retrieve()
                .toBodilessEntity();
    }

    private BigDecimal scoreFraud(Transfer t) {
        try {
            Instant now = Instant.now();
            long frequency = transfers.countByUserIdAndDebitAccountIdAndCreatedAtAfter(
                    t.getUserId(), t.getDebitAccountId(), now.minus(Duration.ofHours(1)));
            boolean newPayee = !transfers.existsByUserIdAndDebitAccountIdAndCreditAccountIdAndStatus(
                    t.getUserId(), t.getDebitAccountId(), t.getCreditAccountId(), "COMPLETED");
            Map<?, ?> resp = rest().post().uri(fraudUrl + "/internal/score")
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", t.getAmount(), "channel", t.getChannel(), "userId", t.getUserId(),
                            "debitAccountId", t.getDebitAccountId(), "hour", now.atZone(java.time.ZoneOffset.UTC).getHour(),
                            "frequency_1h", Math.min(frequency, 100), "new_payee", newPayee ? 1 : 0, "international", 0))
                    .retrieve()
                    .body(Map.class);
            if (resp != null && resp.get("score") != null) {
                return new BigDecimal(resp.get("score").toString());
            }
        } catch (Exception ignored) {
            // fail-open with conservative local heuristic if ML is down
        }
        return t.getAmount().compareTo(new BigDecimal("200000")) > 0 ? new BigDecimal("0.70") : new BigDecimal("0.12");
    }

    private RestClient rest() {
        RestClient current = rest;
        if (current == null) {
            synchronized (this) {
                current = rest;
                if (current == null) rest = current = RestClient.create();
            }
        }
        return current;
    }

    private void enqueue(String topic, Map<String, Object> payload) {
        try {
            OutboxEvent e = new OutboxEvent();
            e.setTopic(topic);
            e.setPayload(mapper.writeValueAsString(payload));
            outbox.save(e);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public List<TransferView> history(Long userId, Long accountId) {
        if (accountId != null) {
            return transfers.findByDebitAccountIdOrCreditAccountIdOrderByCreatedAtDesc(accountId, accountId)
                    .stream().filter(t -> t.getUserId().equals(userId) || com.novabank.common.security.SecuritySupport.hasRole("ADMIN"))
                    .map(TransferView::of).toList();
        }
        return transfers.findByUserIdOrderByCreatedAtDesc(userId).stream().map(TransferView::of).toList();
    }

    public Map<String, Object> statement(Long userId, Long accountId) {
        var rows = history(userId, accountId);
        BigDecimal out = rows.stream().filter(r -> r.debitAccountId().equals(accountId) && "COMPLETED".equals(r.status()))
                .map(TransferView::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal in = rows.stream().filter(r -> r.creditAccountId().equals(accountId) && "COMPLETED".equals(r.status()))
                .map(TransferView::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of("accountId", accountId, "inflow", in, "outflow", out, "net", in.subtract(out), "entries", rows);
    }

    @Transactional
    public ScheduledPayment schedule(Long userId, ScheduledPayment p) {
        p.setUserId(userId);
        p.setStatus("PENDING");
        p.setAmount(Money.normalize(p.getAmount()));
        return scheduled.save(p);
    }

    public List<ScheduledPayment> mySchedule(Long userId) {
        return scheduled.findByUserId(userId);
    }

    public boolean rateLimit(Long userId) {
        String k = "rl:txn:" + userId;
        Long v = redis.opsForValue().increment(k);
        if (v != null && v == 1L) {
            redis.expire(k, Duration.ofMinutes(1));
        }
        return v != null && v > 30;
    }
}
