package com.novabank.account.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fixed_deposits")
public class FixedDeposit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "account_id", nullable = false)
    private Long accountId;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal principal;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal rate;
    @Column(name = "tenure_months", nullable = false)
    private int tenureMonths;
    private String status = "ACTIVE";
    @Column(name = "matured_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal maturedAmount;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public BigDecimal getPrincipal() { return principal; }
    public void setPrincipal(BigDecimal principal) { this.principal = principal; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
    public int getTenureMonths() { return tenureMonths; }
    public void setTenureMonths(int tenureMonths) { this.tenureMonths = tenureMonths; }
    public String getStatus() { return status; }
    public BigDecimal getMaturedAmount() { return maturedAmount; }
    public void setMaturedAmount(BigDecimal maturedAmount) { this.maturedAmount = maturedAmount; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }
}
