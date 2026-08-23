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

@Entity
@Table(name = "eligibility_rule")
public class EligibilityRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_version_id", nullable = false)
    private ProductRuleVersion productRuleVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 40)
    private EligibilityRuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 30)
    private EligibilityRuleOperator operator;

    @Column(name = "comparison_value", nullable = false, length = 200)
    private String value;

    @Column(name = "description", length = 500)
    private String description;

    protected EligibilityRule() {
    }

    public EligibilityRule(
            ProductRuleVersion productRuleVersion,
            EligibilityRuleType ruleType,
            EligibilityRuleOperator operator,
            String value,
            String description
    ) {
        this.productRuleVersion = productRuleVersion;
        this.ruleType = ruleType;
        this.operator = operator;
        this.value = value;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public ProductRuleVersion getProductRuleVersion() {
        return productRuleVersion;
    }

    public EligibilityRuleType getRuleType() {
        return ruleType;
    }

    public EligibilityRuleOperator getOperator() {
        return operator;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }
}
