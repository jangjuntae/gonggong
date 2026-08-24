package com.gonggong.policyfinance.domain.application;

import com.gonggong.policyfinance.domain.product.EligibilityRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "screening_result")
public class ScreeningResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "screening_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private PolicyFinanceApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eligibility_rule_id", nullable = false, updatable = false)
    private EligibilityRule eligibilityRule;

    @Column(name = "passed", nullable = false)
    private boolean passed;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "checked_at", nullable = false, updatable = false)
    private Instant checkedAt;

    protected ScreeningResult() {
    }

    public ScreeningResult(
            PolicyFinanceApplication application,
            EligibilityRule eligibilityRule,
            boolean passed,
            String reason,
            Instant checkedAt
    ) {
        this.application = application;
        this.eligibilityRule = eligibilityRule;
        this.passed = passed;
        this.reason = reason;
        this.checkedAt = checkedAt;
    }

    public Long getId() {
        return id;
    }

    public PolicyFinanceApplication getApplication() {
        return application;
    }

    public EligibilityRule getEligibilityRule() {
        return eligibilityRule;
    }

    public boolean isPassed() {
        return passed;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }
}
