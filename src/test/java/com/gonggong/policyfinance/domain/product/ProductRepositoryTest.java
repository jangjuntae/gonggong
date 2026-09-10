package com.gonggong.policyfinance.domain.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryTest {

    private static final Instant VERSION_ONE_START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant VERSION_TWO_START = Instant.parse("2027-01-01T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private FinancialProductRepository financialProductRepository;

    @Autowired
    private ProductRuleVersionRepository productRuleVersionRepository;

    @Autowired
    private EligibilityRuleRepository eligibilityRuleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesFinancialProductWithExactMaximumAmount() {
        FinancialProduct product = financialProductRepository.saveAndFlush(product("YOUTH-LOAN"));

        assertThat(product.getId()).isNotNull();
        assertThat(product.getMaxApplicationAmount()).isEqualByComparingTo("50000000.00");
        assertThat(product.getCreatedAt()).isNotNull();
        assertThat(financialProductRepository.findByProductCode("YOUTH-LOAN"))
                .contains(product);
    }

    @Test
    void rejectsDuplicateProductCode() {
        financialProductRepository.saveAndFlush(product("DUPLICATE-CODE"));

        assertThatThrownBy(() -> financialProductRepository.saveAndFlush(product("DUPLICATE-CODE")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savesMultipleVersionsForOneProduct() {
        FinancialProduct product = financialProductRepository.saveAndFlush(product("MULTI-VERSION"));

        ProductRuleVersion versionOne = productRuleVersionRepository.save(
                ruleVersion(product, 1, VERSION_ONE_START, VERSION_TWO_START, false, 12)
        );
        ProductRuleVersion versionTwo = productRuleVersionRepository.saveAndFlush(
                ruleVersion(product, 2, VERSION_TWO_START, null, true, 24)
        );

        assertThat(versionOne.getId()).isNotNull();
        assertThat(versionTwo.getId()).isNotNull();
        assertThat(productRuleVersionRepository.findByFinancialProductIdAndVersion(product.getId(), 2))
                .contains(versionTwo);
    }

    @Test
    void rejectsDuplicateVersionWithinSameProduct() {
        FinancialProduct product = financialProductRepository.saveAndFlush(product("DUPLICATE-VERSION"));
        productRuleVersionRepository.saveAndFlush(
                ruleVersion(product, 1, VERSION_ONE_START, null, true, 12)
        );

        assertThatThrownBy(() -> productRuleVersionRepository.saveAndFlush(
                ruleVersion(product, 1, VERSION_TWO_START, null, false, 24)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsEachDifferentProductToHaveOneActiveVersion() {
        FinancialProduct firstProduct = financialProductRepository.save(product("PRODUCT-A"));
        FinancialProduct secondProduct = financialProductRepository.saveAndFlush(product("PRODUCT-B"));

        ProductRuleVersion firstVersion = productRuleVersionRepository.save(
                ruleVersion(firstProduct, 1, VERSION_ONE_START, null, true, 12)
        );
        ProductRuleVersion secondVersion = productRuleVersionRepository.saveAndFlush(
                ruleVersion(secondProduct, 1, VERSION_ONE_START, null, true, 12)
        );

        assertThat(firstVersion.getId()).isNotNull();
        assertThat(secondVersion.getId()).isNotNull();
    }

    @Test
    void rejectsTwoActiveVersionsForSameProduct() {
        FinancialProduct product = financialProductRepository.saveAndFlush(product("TWO-ACTIVE-VERSIONS"));
        productRuleVersionRepository.saveAndFlush(
                ruleVersion(product, 1, VERSION_ONE_START, VERSION_TWO_START, true, 12)
        );

        assertThatThrownBy(() -> productRuleVersionRepository.saveAndFlush(
                ruleVersion(product, 2, VERSION_TWO_START, null, true, 24)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsMultipleInactiveVersionsForSameProduct() {
        FinancialProduct product = financialProductRepository.saveAndFlush(product("INACTIVE-HISTORY"));

        ProductRuleVersion versionOne = productRuleVersionRepository.save(
                ruleVersion(product, 1, VERSION_ONE_START, VERSION_TWO_START, false, 12)
        );
        ProductRuleVersion versionTwo = productRuleVersionRepository.saveAndFlush(
                ruleVersion(product, 2, VERSION_TWO_START, null, false, 24)
        );

        assertThat(versionOne.getId()).isNotNull();
        assertThat(versionTwo.getId()).isNotNull();
    }

    @Test
    void rejectsNonPositiveRepaymentMonths() {
        FinancialProduct product = financialProductRepository.saveAndFlush(product("INVALID-MONTHS"));

        assertThatThrownBy(() -> productRuleVersionRepository.saveAndFlush(
                ruleVersion(product, 1, VERSION_ONE_START, null, true, 0)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsEffectiveActiveVersionAtGivenTime() {
        FinancialProduct product = financialProductRepository.saveAndFlush(product("CURRENT-VERSION"));
        productRuleVersionRepository.saveAndFlush(
                ruleVersion(product, 1, VERSION_ONE_START, null, true, 36)
        );

        assertThat(productRuleVersionRepository.findEffectiveVersion(
                product.getId(), Instant.parse("2026-06-01T00:00:00Z")
        )).hasValueSatisfying(version -> assertThat(version.getVersion()).isEqualTo(1));
    }

    @Test
    void eligibilityRuleReferencesProductRuleVersion() {
        FinancialProduct product = financialProductRepository.saveAndFlush(product("ELIGIBILITY-RULE"));
        ProductRuleVersion version = productRuleVersionRepository.saveAndFlush(
                ruleVersion(product, 1, VERSION_ONE_START, null, true, 12)
        );

        EligibilityRule rule = eligibilityRuleRepository.saveAndFlush(new EligibilityRule(
                version,
                EligibilityRuleType.AGE,
                EligibilityRuleOperator.BETWEEN,
                "19,34",
                "신청일 기준 연령"
        ));

        assertThat(rule.getId()).isNotNull();
        assertThat(rule.getProductRuleVersion().getId()).isEqualTo(version.getId());
        assertThat(eligibilityRuleRepository.findByProductRuleVersionId(version.getId()))
                .containsExactly(rule);
    }

    @Test
    void flywayAppliedProductMigrationsAndPartialUniqueIndex() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('2', '3') AND success = true",
                Integer.class
        );
        Integer productTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'financial_product'",
                Integer.class
        );

        Integer activeVersionIndexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' "
                        + "AND indexname = 'uk_product_rule_version_one_active_per_product' "
                        + "AND indexdef ILIKE '%WHERE (active = true)%'",
                Integer.class
        );

        assertThat(migrationCount).isEqualTo(2);
        assertThat(productTableCount).isEqualTo(1);
        assertThat(activeVersionIndexCount).isEqualTo(1);
    }

    private FinancialProduct product(String code) {
        return new FinancialProduct(
                code,
                "청년 정책금융",
                "청년 대상 정책금융 상품",
                new BigDecimal("50000000.00"),
                true
        );
    }

    private ProductRuleVersion ruleVersion(
            FinancialProduct product,
            int version,
            Instant effectiveFrom,
            Instant effectiveTo,
            boolean active,
            int repaymentMonths
    ) {
        return new ProductRuleVersion(
                product,
                version,
                effectiveFrom,
                effectiveTo,
                new BigDecimal("3.2500"),
                repaymentMonths,
                RepaymentMethod.EQUAL_PAYMENT,
                active
        );
    }
}
