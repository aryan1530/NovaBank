package com.novabank.txn.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId = UUID.randomUUID();
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;
    @Column(name = "debit_account_id", nullable = false)
    private Long debitAccountId;
    @Column(name = "credit_account_id", nullable = false)
    private Long creditAccountId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;
    private String channel;
    private String status;
    private String narration;
    @Column(name = "fraud_score", precision = 6, scale = 4)
    private BigDecimal fraudScore;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    @Column(name = "completed_at")
    private Instant completedAt;

    public Long getId() { return id; }
    public UUID getPublicId() { return publicId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Long getDebitAccountId() { return debitAccountId; }
    public void setDebitAccountId(Long debitAccountId) { this.debitAccountId = debitAccountId; }
    public Long getCreditAccountId() { return creditAccountId; }
    public void setCreditAccountId(Long creditAccountId) { this.creditAccountId = creditAccountId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
    public BigDecimal getFraudScore() { return fraudScore; }
    public void setFraudScore(BigDecimal fraudScore) { this.fraudScore = fraudScore; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
