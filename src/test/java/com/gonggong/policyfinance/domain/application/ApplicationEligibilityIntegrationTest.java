package com.gonggong.policyfinance.domain.application;

import com.gonggong.policyfinance.domain.customer.Customer;
import com.gonggong.policyfinance.domain.customer.CustomerRepository;
import com.gonggong.policyfinance.domain.product.EligibilityRule;
import com.gonggong.policyfinance.domain.product.EligibilityRuleOperator;
import com.gonggong.policyfinance.domain.product.EligibilityRuleRepository;
import com.gonggong.policyfinance.domain.product.EligibilityRuleType;
import com.gonggong.policyfinance.domain.product.FinancialProduct;
import com.gonggong.policyfinance.domain.product.FinancialProductRepository;
import com.gonggong.policyfinance.domain.product.ProductRuleVersion;
import com.gonggong.policyfinance.domain.product.ProductRuleVersionRepository;
import com.gonggong.policyfinance.domain.product.RepaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Transactional
class ApplicationEligibilityIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-23T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private EligibilityService eligibilityService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private FinancialProductRepository financialProductRepository;

    @Autowired
    private ProductRuleVersionRepository productRuleVersionRepository;

    @Autowired
    private EligibilityRuleRepository eligibilityRuleRepository;

    @Autowired
    private PolicyFinanceApplicationRepository applicationRepository;

    @Autowired
    private ScreeningResultRepository screeningResultRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsDraftApplicationWithCurrentRuleVersion() {
        Customer customer = saveCustomer("CUSTOMER-001");
        FinancialProduct product = saveProduct("PRODUCT-001");
        ProductRuleVersion ruleVersion = saveRuleVersion(product, 1, true);

        PolicyFinanceApplication application = applicationService.createApplication(
                customer.getId(), product.getId(), new BigDecimal("30000000.00")
        );

        assertThat(application.getId()).isNotNull();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.DRAFT);
        assertThat(application.getProductRuleVersion().getId()).isEqualTo(ruleVersion.getId());
    }

    @Test
    void rejectsApplicationWhenProductHasNoActiveRuleVersion() {
        Customer customer = saveCustomer("CUSTOMER-002");
        FinancialProduct product = saveProduct("PRODUCT-002");
        saveRuleVersion(product, 1, false);

        assertThatThrownBy(() -> applicationService.createApplication(
                customer.getId(), product.getId(), new BigDecimal("30000000.00")
        )).isInstanceOf(ApplicationBusinessException.class)
                .hasMessageContaining("Active product rule version not found");
    }

    @Test
    void rejectsNonPositiveAndOverLimitRequestedAmount() {
        Customer customer = saveCustomer("CUSTOMER-003");
        FinancialProduct product = saveProduct("PRODUCT-003");
        saveRuleVersion(product, 1, true);

        assertThatThrownBy(() -> applicationService.createApplication(
                customer.getId(), product.getId(), BigDecimal.ZERO
        )).isInstanceOf(ApplicationBusinessException.class)
                .hasMessageContaining("greater than zero");

        assertThatThrownBy(() -> applicationService.createApplication(
                customer.getId(), product.getId(), new BigDecimal("50000000.01")
        )).isInstanceOf(ApplicationBusinessException.class)
                .hasMessageContaining("exceeds product maximum");
    }

    @Test
    void submittedApplicationMovesFromDraftToReceivedAndCannotBeSubmittedAgain() {
        PolicyFinanceApplication application = createApplication("CUSTOMER-004", "PRODUCT-004");

        PolicyFinanceApplication submitted = applicationService.submitApplication(application.getId());

        assertThat(submitted.getStatus()).isEqualTo(ApplicationStatus.RECEIVED);
        assertThat(submitted.getSubmittedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> applicationService.submitApplication(application.getId()))
                .isInstanceOf(InvalidApplicationStatusTransitionException.class);
    }

    @Test
    void policyChangeDoesNotChangeStoredApplicationRuleVersionOrEvaluation() {
        Customer customer = saveCustomer("CUSTOMER-005");
        FinancialProduct product = saveProduct("PRODUCT-005");
        ProductRuleVersion versionOne = saveRuleVersion(product, 1, true);
        saveRule(versionOne, EligibilityRuleType.REGION, EligibilityRuleOperator.EQUAL, "SEOUL");
        PolicyFinanceApplication application = applicationService.createApplication(
                customer.getId(), product.getId(), new BigDecimal("30000000.00")
        );

        versionOne.deactivate();
        productRuleVersionRepository.flush();
        ProductRuleVersion versionTwo = saveRuleVersion(product, 2, true);
        saveRule(versionTwo, EligibilityRuleType.REGION, EligibilityRuleOperator.EQUAL, "BUSAN");

        applicationService.submitApplication(application.getId());
        EligibilityEvaluation evaluation = eligibilityService.evaluate(application.getId());
        PolicyFinanceApplication stored = applicationRepository.findById(application.getId()).orElseThrow();

        assertThat(stored.getProductRuleVersion().getId()).isEqualTo(versionOne.getId());
        assertThat(stored.getProductRuleVersion().getId()).isNotEqualTo(versionTwo.getId());
        assertThat(evaluation.eligible()).isTrue();
        assertThat(evaluation.results()).extracting(ScreeningResult::getEligibilityRule)
                .extracting(EligibilityRule::getProductRuleVersion)
                .extracting(ProductRuleVersion::getId)
                .containsOnly(versionOne.getId());
    }

    @Test
    void allSupportedRulesPassAndResultsAreStored() {
        PolicyFinanceApplication application = createApplicationWithRules(
                "CUSTOMER-006",
                "PRODUCT-006",
                List.of(
                        rule(EligibilityRuleType.AGE, EligibilityRuleOperator.BETWEEN, "19,34"),
                        rule(EligibilityRuleType.INCOME, EligibilityRuleOperator.LESS_THAN_OR_EQUAL, "40000000"),
                        rule(EligibilityRuleType.CREDIT_SCORE, EligibilityRuleOperator.GREATER_THAN_OR_EQUAL, "700"),
                        rule(EligibilityRuleType.REGION, EligibilityRuleOperator.EQUAL, "SEOUL"),
                        rule(EligibilityRuleType.BUSINESS_PERIOD, EligibilityRuleOperator.GREATER_THAN_OR_EQUAL, "24")
                )
        );

        EligibilityEvaluation evaluation = eligibilityService.evaluate(application.getId());

        assertThat(evaluation.eligible()).isTrue();
        assertThat(evaluation.results()).hasSize(5).allMatch(ScreeningResult::isPassed);
        assertThat(screeningResultRepository.findByApplicationIdOrderById(application.getId())).hasSize(5);
    }

    @Test
    void rejectsEligibilityEvaluationWhenRuleVersionHasNoRules() {
        PolicyFinanceApplication application = createApplication("CUSTOMER-NO-RULE", "PRODUCT-NO-RULE");
        applicationService.submitApplication(application.getId());

        assertThatThrownBy(() -> eligibilityService.evaluate(application.getId()))
                .isInstanceOf(ApplicationBusinessException.class)
                .hasMessageContaining("No eligibility rules configured")
                .hasMessageContaining(application.getProductRuleVersion().getId().toString());
        assertThat(screeningResultRepository.findByApplicationIdOrderById(application.getId())).isEmpty();
    }

    @Test
    void rejectsApplicationEntityWithRuleVersionFromDifferentProduct() {
        Customer customer = saveCustomer("CUSTOMER-MISMATCH-ENTITY");
        FinancialProduct applicationProduct = saveProduct("PRODUCT-MISMATCH-A");
        FinancialProduct versionProduct = saveProduct("PRODUCT-MISMATCH-B");
        ProductRuleVersion otherProductVersion = saveRuleVersion(versionProduct, 1, true);

        assertThatThrownBy(() -> new PolicyFinanceApplication(
                customer,
                applicationProduct,
                otherProductVersion,
                new BigDecimal("30000000.00")
        )).isInstanceOf(ApplicationBusinessException.class)
                .hasMessageContaining("does not belong to financial product");
    }

    @Test
    void databaseRejectsApplicationWithRuleVersionFromDifferentProduct() {
        Customer customer = saveCustomer("CUSTOMER-MISMATCH-DB");
        FinancialProduct applicationProduct = saveProduct("PRODUCT-MISMATCH-DB-A");
        FinancialProduct versionProduct = saveProduct("PRODUCT-MISMATCH-DB-B");
        ProductRuleVersion otherProductVersion = saveRuleVersion(versionProduct, 1, true);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO application "
                        + "(customer_id, product_id, rule_version_id, status, requested_amount) "
                        + "VALUES (?, ?, ?, 'DRAFT', 30000000.00)",
                customer.getId(),
                applicationProduct.getId(),
                otherProductVersion.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseAcceptsApplicationWithMatchingProductAndRuleVersion() {
        Customer customer = saveCustomer("CUSTOMER-MATCH-DB");
        FinancialProduct product = saveProduct("PRODUCT-MATCH-DB");
        ProductRuleVersion ruleVersion = saveRuleVersion(product, 1, true);

        int insertedRows = jdbcTemplate.update(
                "INSERT INTO application "
                        + "(customer_id, product_id, rule_version_id, status, requested_amount) "
                        + "VALUES (?, ?, ?, 'DRAFT', 30000000.00)",
                customer.getId(),
                product.getId(),
                ruleVersion.getId()
        );

        assertThat(insertedRows).isEqualTo(1);
    }

    @Test
    void ageRuleFailureMakesApplicationIneligible() {
        assertFailedRule(
                "CUSTOMER-AGE",
                "PRODUCT-AGE",
                EligibilityRuleType.AGE,
                EligibilityRuleOperator.GREATER_THAN_OR_EQUAL,
                "30"
        );
    }

    @Test
    void incomeRuleFailureMakesApplicationIneligible() {
        assertFailedRule(
                "CUSTOMER-INCOME",
                "PRODUCT-INCOME",
                EligibilityRuleType.INCOME,
                EligibilityRuleOperator.LESS_THAN_OR_EQUAL,
                "30000000"
        );
    }

    @Test
    void creditScoreRuleFailureMakesApplicationIneligible() {
        assertFailedRule(
                "CUSTOMER-CREDIT",
                "PRODUCT-CREDIT",
                EligibilityRuleType.CREDIT_SCORE,
                EligibilityRuleOperator.GREATER_THAN_OR_EQUAL,
                "800"
        );
    }

    @Test
    void regionRuleFailureMakesApplicationIneligible() {
        assertFailedRule(
                "CUSTOMER-REGION",
                "PRODUCT-REGION",
                EligibilityRuleType.REGION,
                EligibilityRuleOperator.EQUAL,
                "BUSAN"
        );
    }

    @Test
    void flywayAppliedCustomerApplicationMigrations() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('4', '5') AND success = true",
                Integer.class
        );
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' "
                        + "AND table_name IN ('customer', 'application', 'screening_result')",
                Integer.class
        );

        assertThat(migrationCount).isEqualTo(2);
        assertThat(tableCount).isEqualTo(3);
    }

    private void assertFailedRule(
            String customerNo,
            String productCode,
            EligibilityRuleType type,
            EligibilityRuleOperator operator,
            String value
    ) {
        PolicyFinanceApplication application = createApplicationWithRules(
                customerNo,
                productCode,
                List.of(rule(type, operator, value))
        );

        EligibilityEvaluation evaluation = eligibilityService.evaluate(application.getId());

        assertThat(evaluation.eligible()).isFalse();
        assertThat(evaluation.results()).singleElement().satisfies(result -> {
            assertThat(result.isPassed()).isFalse();
            assertThat(result.getReason()).contains(type.name()).contains("failed");
        });
    }

    private PolicyFinanceApplication createApplication(String customerNo, String productCode) {
        Customer customer = saveCustomer(customerNo);
        FinancialProduct product = saveProduct(productCode);
        saveRuleVersion(product, 1, true);
        return applicationService.createApplication(
                customer.getId(), product.getId(), new BigDecimal("30000000.00")
        );
    }

    private PolicyFinanceApplication createApplicationWithRules(
            String customerNo,
            String productCode,
            List<RuleDefinition> rules
    ) {
        Customer customer = saveCustomer(customerNo);
        FinancialProduct product = saveProduct(productCode);
        ProductRuleVersion ruleVersion = saveRuleVersion(product, 1, true);
        rules.forEach(rule -> saveRule(ruleVersion, rule.type(), rule.operator(), rule.value()));
        PolicyFinanceApplication application = applicationService.createApplication(
                customer.getId(), product.getId(), new BigDecimal("30000000.00")
        );
        return applicationService.submitApplication(application.getId());
    }

    private Customer saveCustomer(String customerNo) {
        return customerRepository.saveAndFlush(new Customer(
                customerNo,
                "테스트고객",
                LocalDate.of(1997, 6, 15),
                new BigDecimal("35000000.00"),
                750,
                "SEOUL",
                LocalDate.of(2024, 8, 1)
        ));
    }

    private FinancialProduct saveProduct(String productCode) {
        return financialProductRepository.saveAndFlush(new FinancialProduct(
                productCode,
                "테스트 정책금융",
                "가상 데이터 전용 상품",
                new BigDecimal("50000000.00"),
                true
        ));
    }

    private ProductRuleVersion saveRuleVersion(FinancialProduct product, int version, boolean active) {
        return productRuleVersionRepository.saveAndFlush(new ProductRuleVersion(
                product,
                version,
                Instant.parse("2025-01-01T00:00:00Z"),
                null,
                new BigDecimal("3.2500"),
                24,
                RepaymentMethod.EQUAL_PAYMENT,
                active
        ));
    }

    private EligibilityRule saveRule(
            ProductRuleVersion version,
            EligibilityRuleType type,
            EligibilityRuleOperator operator,
            String value
    ) {
        return eligibilityRuleRepository.saveAndFlush(new EligibilityRule(
                version,
                type,
                operator,
                value,
                type + " 테스트 규칙"
        ));
    }

    private RuleDefinition rule(
            EligibilityRuleType type,
            EligibilityRuleOperator operator,
            String value
    ) {
        return new RuleDefinition(type, operator, value);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    private record RuleDefinition(
            EligibilityRuleType type,
            EligibilityRuleOperator operator,
            String value
    ) {
    }
}
