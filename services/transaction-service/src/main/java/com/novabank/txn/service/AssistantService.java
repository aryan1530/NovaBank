package com.novabank.txn.service;

import com.novabank.common.exception.BusinessException;
import com.novabank.txn.domain.AssistantAudit;
import com.novabank.txn.repo.AssistantAuditRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AssistantService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final TransferService transfers;
    private final AssistantAuditRepository audit;
    private final AssistantRateLimiter rateLimiter;
    private volatile RestClient rest;
    private final String accountUrl;
    private final String internalToken;

    public AssistantService(TransferService transfers, AssistantAuditRepository audit,
                            AssistantRateLimiter rateLimiter,
                            @Value("${novabank.account-service-url}") String accountUrl,
                            @Value("${novabank.internal-token}") String internalToken) {
        this.transfers = transfers;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.accountUrl = accountUrl;
        this.internalToken = internalToken;
    }

    public record Reply(String message, String intent, String dataSource, boolean safeMode, Instant answeredAt) {}
    public record AccountSnapshot(Long id, String accountNumber, String type, String currency,
                                  BigDecimal balance, String status) {}
    private enum Intent { BALANCE, RECENT_ACTIVITY, SPENDING, TRANSFER_GUIDANCE, PRIVACY, HELP }

    public Reply reply(Long userId, String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.strip();
        if (message.isBlank() || message.length() > 500) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSISTANT_MESSAGE", "Message must contain 1 to 500 characters");
        }
        rateLimiter.check(userId);
        Intent intent = classify(message.toLowerCase(Locale.ROOT));
        audit.save(new AssistantAudit(userId, intent.name(), sha256(message)));
        String answer = switch (intent) {
            case BALANCE -> balance(userId);
            case RECENT_ACTIVITY -> recentActivity(userId);
            case SPENDING -> spending(userId);
            case TRANSFER_GUIDANCE -> "I can guide you, but I can’t initiate or approve payments. Open Transfers, review the recipient and amount, then confirm there. Payments in this demo are simulated.";
            case PRIVACY -> "I can only help with the accounts and activity belonging to your signed-in profile. I can’t look up another customer or use a customer ID to bypass account access checks.";
            case HELP -> "I can help with your recorded account balances, recent transfers, 30-day recorded outflows, transfer status meanings, and banking-service guidance. Try asking ‘What is my balance?’ or ‘Show recent activity’.";
        };
        return new Reply(answer, intent.name(), intent == Intent.HELP || intent == Intent.TRANSFER_GUIDANCE || intent == Intent.PRIVACY
                ? "BANKING_GUIDANCE" : "AUTHENTICATED_BANKING_RECORDS", true, Instant.now());
    }

    private Intent classify(String text) {
        if (containsAny(text, "another customer", "other customer", "someone else's", "somebody else's", "other user's", "other user", "customer id", "user id")) return Intent.PRIVACY;
        if (containsAny(text, "transfer", "send money", "make a payment", "pay someone", "beneficiary", "move money")) return Intent.TRANSFER_GUIDANCE;
        if (containsAny(text, "spending", "spent", "spend", "outflow", "where did my money go")) return Intent.SPENDING;
        if (containsAny(text, "balance", "how much money", "my accounts", "account summary")) return Intent.BALANCE;
        if (containsAny(text, "transaction", "activity", "payment status", "transfer status", "recent", "pending")) return Intent.RECENT_ACTIVITY;
        return Intent.HELP;
    }

    private List<AccountSnapshot> accounts(Long userId) {
        try {
            List<AccountSnapshot> rows = rest().get()
                    .uri(accountUrl + "/internal/accounts/by-user/" + userId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return rows == null ? List.of() : rows;
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "ASSISTANT_DATA_UNAVAILABLE", "Your account data is temporarily unavailable. Please try again shortly.");
        }
    }

    private String balance(Long userId) {
        List<AccountSnapshot> own = accounts(userId);
        if (own.isEmpty()) return "There are no accounts linked to your profile yet. You can open a savings account from the Overview page.";
        BigDecimal total = own.stream().map(AccountSnapshot::balance).filter(v -> v != null).reduce(ZERO, BigDecimal::add);
        String rows = own.stream().map(a -> a.type() + " ending " + lastFour(a.accountNumber()) + ": " + money(a.balance()) + " " + a.currency() + " (" + a.status().toLowerCase(Locale.ROOT) + ")")
                .collect(Collectors.joining("; "));
        return "Your recorded total balance is " + money(total) + " INR across " + own.size() + (own.size() == 1 ? " account. " : " accounts. ") + rows + ".";
    }

    private String recentActivity(Long userId) {
        List<TransferService.TransferView> rows = transfers.history(userId, null);
        if (rows.isEmpty()) return "I don’t see any transfers recorded for your profile yet.";
        String details = rows.stream().limit(5).map(t -> t.channel() + " transfer " + t.publicId() + " for " + money(t.amount()) + " INR is " + t.status().toLowerCase(Locale.ROOT).replace('_', ' ') + " (" + t.createdAt() + ")")
                .collect(Collectors.joining("; "));
        return "Here are your latest recorded transfers: " + details + ". A completed transfer has posted; pending review means the fraud policy is holding it; held fraud requires an analyst decision; failed means processing did not complete.";
    }

    private String spending(Long userId) {
        List<AccountSnapshot> own = accounts(userId);
        Set<Long> ownIds = own.stream().map(AccountSnapshot::id).collect(Collectors.toSet());
        Instant since = Instant.now().minus(Duration.ofDays(30));
        List<TransferService.TransferView> rows = transfers.history(userId, null).stream()
                .filter(t -> t.createdAt() != null && t.createdAt().isAfter(since))
                .filter(t -> "COMPLETED".equals(t.status()) && ownIds.contains(t.debitAccountId()))
                .toList();
        BigDecimal total = rows.stream().map(TransferService.TransferView::amount).reduce(ZERO, BigDecimal::add);
        Map<String, BigDecimal> channels = rows.stream().collect(Collectors.groupingBy(TransferService.TransferView::channel,
                Collectors.mapping(TransferService.TransferView::amount, Collectors.reducing(ZERO, BigDecimal::add))));
        String breakdown = channels.entrySet().stream().map(e -> e.getKey() + " " + money(e.getValue()))
                .collect(Collectors.joining(", "));
        return "Your recorded completed outflows for the last 30 days total " + money(total) + " INR across " + rows.size() + " transfers" + (breakdown.isBlank() ? "." : ". By simulated channel: " + breakdown + ".") + " This summarizes NovaBank records, not card or external-bank spending.";
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

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }
    private String money(BigDecimal amount) { return (amount == null ? ZERO : amount).setScale(2).toPlainString(); }
    private String lastFour(String number) { return number == null || number.length() < 4 ? "****" : "****" + number.substring(number.length() - 4); }
    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }
}
