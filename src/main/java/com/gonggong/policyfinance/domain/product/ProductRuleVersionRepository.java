package com.gonggong.policyfinance.domain.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ProductRuleVersionRepository extends JpaRepository<ProductRuleVersion, Long> {

    Optional<ProductRuleVersion> findByFinancialProductIdAndVersion(Long productId, int version);

    @Query("""
            select ruleVersion
            from ProductRuleVersion ruleVersion
            where ruleVersion.financialProduct.id = :productId
              and ruleVersion.active = true
              and ruleVersion.effectiveFrom <= :at
              and (ruleVersion.effectiveTo is null or ruleVersion.effectiveTo > :at)
            """)
    Optional<ProductRuleVersion> findEffectiveVersion(
            @Param("productId") Long productId,
            @Param("at") Instant at
    );
}
