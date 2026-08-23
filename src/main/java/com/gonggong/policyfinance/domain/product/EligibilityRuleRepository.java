package com.gonggong.policyfinance.domain.product;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EligibilityRuleRepository extends JpaRepository<EligibilityRule, Long> {

    List<EligibilityRule> findByProductRuleVersionId(Long ruleVersionId);
}
