package com.novabank.ledger.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "ran_at")
    private Instant ranAt = Instant.now();
    private boolean balanced;
    @Column(name = "debit_total", precision = 18, scale = 2)
    private BigDecimal debitTotal;
    @Column(name = "credit_total", precision = 18, scale = 2)
    private BigDecimal creditTotal;
    private String notes;

    public Long getId() { return id; }
    public Instant getRanAt() { return ranAt; }
    public boolean isBalanced() { return balanced; }
    public void setBalanced(boolean balanced) { this.balanced = balanced; }
    public BigDecimal getDebitTotal() { return debitTotal; }
    public void setDebitTotal(BigDecimal debitTotal) { this.debitTotal = debitTotal; }
    public BigDecimal getCreditTotal() { return creditTotal; }
    public void setCreditTotal(BigDecimal creditTotal) { this.creditTotal = creditTotal; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
