package com.gonggong.policyfinance.domain.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "product_rule_version",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_rule_version_product_version",
                columnNames = {"product_id", "version_no"}
        )
)
public class ProductRuleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_version_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private FinancialProduct financialProduct;

    @Column(name = "version_no", nullable = false)
    private int version;

    @Column(name = "valid_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "valid_to")
    private Instant effectiveTo;

    @Column(name = "interest_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "repayment_months", nullable = false)
    private int repaymentMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_method", nullable = false, length = 30)
    private RepaymentMethod repaymentMethod;

    @Column(name = "active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProductRuleVersion() {
    }

    public ProductRuleVersion(
            FinancialProduct financialProduct,
            int version,
            Instant effectiveFrom,
            Instant effectiveTo,
            BigDecimal interestRate,
            int repaymentMonths,
            RepaymentMethod repaymentMethod,
            boolean active
    ) {
        this.financialProduct = financialProduct;
        this.version = version;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.interestRate = interestRate;
        this.repaymentMonths = repaymentMonths;
        this.repaymentMethod = repaymentMethod;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public FinancialProduct getFinancialProduct() {
        return financialProduct;
    }

    public int getVersion() {
        return version;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public int getRepaymentMonths() {
        return repaymentMonths;
    }

    public RepaymentMethod getRepaymentMethod() {
        return repaymentMethod;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
