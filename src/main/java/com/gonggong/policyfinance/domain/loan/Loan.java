package com.gonggong.policyfinance.domain.loan;

import com.gonggong.policyfinance.domain.application.PolicyFinanceApplication;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "loan",
        uniqueConstraints = @UniqueConstraint(name = "uk_loan_application", columnNames = "application_id")
)
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "loan_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private PolicyFinanceApplication application;

    @Column(name = "principal", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal principalAmount;

    @Column(name = "outstanding_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal outstandingBalance;

    @Column(name = "interest_rate", nullable = false, precision = 7, scale = 4, updatable = false)
    private BigDecimal interestRate;

    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Loan() {
    }

    public Loan(
            PolicyFinanceApplication application,
            BigDecimal principalAmount,
            BigDecimal interestRate,
            Instant executedAt
    ) {
        this.application = application;
        this.principalAmount = principalAmount;
        this.outstandingBalance = principalAmount;
        this.interestRate = interestRate;
        this.executedAt = executedAt;
    }

    public Long getId() {
        return id;
    }

    public PolicyFinanceApplication getApplication() {
        return application;
    }

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public BigDecimal getOutstandingBalance() {
        return outstandingBalance;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
