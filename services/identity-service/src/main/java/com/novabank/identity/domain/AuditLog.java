package com.novabank.identity.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id")
    private Long userId;
    private String action;
    private String detail;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public static AuditLog of(Long userId, String action, String detail) {
        AuditLog log = new AuditLog();
        log.userId = userId;
        log.action = action;
        log.detail = detail;
        return log;
    }
}
