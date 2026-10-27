package com.novabank.txn.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "assistant_audit")
public class AssistantAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 32)
    private String intent;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AssistantAudit() {}
    public AssistantAudit(Long userId, String intent, String requestHash) {
        this.userId = userId;
        this.intent = intent;
        this.requestHash = requestHash;
    }
}
