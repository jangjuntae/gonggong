package com.gonggong.policyfinance.domain.loan;

import com.gonggong.policyfinance.domain.application.ApplicationAssignmentService;
import com.gonggong.policyfinance.domain.application.ApplicationBusinessException;
import com.gonggong.policyfinance.domain.application.ApplicationReviewRepository;
import com.gonggong.policyfinance.domain.application.ApplicationService;
import com.gonggong.policyfinance.domain.application.ApplicationStatus;
import com.gonggong.policyfinance.domain.application.ApplicationWorkflowService;
import com.gonggong.policyfinance.domain.application.EligibilityService;
import com.gonggong.policyfinance.domain.application.PolicyFinanceApplication;
import com.gonggong.policyfinance.domain.application.PolicyFinanceApplicationRepository;
import com.gonggong.policyfinance.domain.application.StatusHistory;
import com.gonggong.policyfinance.domain.application.StatusHistoryRepository;
import com.gonggong.policyfinance.domain.customer.Customer;
import com.gonggong.policyfinance.domain.customer.CustomerRepository;
import com.gonggong.policyfinance.domain.organization.Department;
import com.gonggong.policyfinance.domain.organization.DepartmentRepository;
import com.gonggong.policyfinance.domain.organization.Employee;
import com.gonggong.policyfinance.domain.organization.EmployeeRepository;
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
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Transactional
class LoanExecutionIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired LoanExecutionService loanExecutionService;
    @Autowired LoanRepository loanRepository;
    @Autowired ApplicationService applicationService;
    @Autowired ApplicationWorkflowService workflowService;
    @Autowired EligibilityService eligibilityService;
    @Autowired ApplicationAssignmentService assignmentService;
    @Autowired PolicyFinanceApplicationRepository applicationRepository;
    @Autowired StatusHistoryRepository statusHistoryRepository;
    @Autowired ApplicationReviewRepository reviewRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired FinancialProductRepository productRepository;
    @Autowired ProductRuleVersionRepository ruleVersionRepository;
    @Autowired EligibilityRuleRepository eligibilityRuleRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void executesApprovedApplicationAtomically() {
        ApprovedFixture fixture = approvedFixture();

        Loan loan = loanExecutionService.executeLoan(fixture.application().getId(), "Approved loan execution");
        PolicyFinanceApplication storedApplication = applicationRepository
                .findById(fixture.application().getId()).orElseThrow();
        List<StatusHistory> histories = statusHistoryRepository
                .findByApplicationIdOrderByChangedAt(fixture.application().getId());
        StatusHistory executionHistory = histories.get(histories.size() - 1);

        assertThat(loan.getId()).isNotNull();
        assertThat(loan.getApplication().getId()).isEqualTo(fixture.application().getId());
        assertThat(loan.getPrincipalAmount()).isEqualByComparingTo("20000000.00");
        assertThat(loan.getOutstandingBalance()).isEqualByComparingTo("20000000.00");
        assertThat(loan.getInterestRate()).isEqualByComparingTo("3.2500");
        assertThat(loan.getExecutedAt()).isEqualTo(NOW);
        assertThat(storedApplication.getStatus()).isEqualTo(ApplicationStatus.EXECUTED);
        assertThat(executionHistory.getFromStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(executionHistory.getToStatus()).isEqualTo(ApplicationStatus.EXECUTED);
        assertThat(executionHistory.getChangedBy()).isNull();
        assertThat(executionHistory.getReason()).isEqualTo("Approved loan execution");
        assertThat(executionHistory.getChangedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsExecutionForApplicationUnderReviewWithoutPartialData() {
        ReviewFixture fixture = reviewFixture();
        int historyCount = statusHistoryRepository
                .findByApplicationIdOrderByChangedAt(fixture.application().getId()).size();

        assertThatThrownBy(() -> loanExecutionService.executeLoan(
                fixture.application().getId(), "Premature execution"
        )).isInstanceOf(ApplicationBusinessException.class)
                .hasMessageContaining("approved application");

        assertThat(loanRepository.findByApplicationId(fixture.application().getId())).isEmpty();
        assertThat(applicationRepository.findById(fixture.application().getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.UNDER_REVIEW);
        assertThat(statusHistoryRepository.findByApplicationIdOrderByChangedAt(fixture.application().getId()))
                .hasSize(historyCount);
    }

    @Test
    void rejectsExecutionForRejectedApplication() {
        ReviewFixture fixture = reviewFixture();
        workflowService.reject(fixture.application().getId(), fixture.reviewer().getId(), "Review rejected");

        assertThatThrownBy(() -> loanExecutionService.executeLoan(
                fixture.application().getId(), "Invalid rejected execution"
        )).isInstanceOf(ApplicationBusinessException.class)
                .hasMessageContaining("approved application");
        assertThat(loanRepository.findByApplicationId(fixture.application().getId())).isEmpty();
        assertThat(applicationRepository.findById(fixture.application().getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.REJECTED);
    }

    @Test
    void duplicateExecutionFailsAndLeavesExactlyOneLoan() {
        ApprovedFixture fixture = approvedFixture();
        loanExecutionService.executeLoan(fixture.application().getId(), "First execution");

        assertThatThrownBy(() -> loanExecutionService.executeLoan(
                fixture.application().getId(), "Duplicate execution"
        )).isInstanceOf(ApplicationBusinessException.class)
                .hasMessageContaining("already been executed");

        assertThat(loanRepository.findAll())
                .filteredOn(loan -> loan.getApplication().getId().equals(fixture.application().getId()))
                .hasSize(1);
    }

    @Test
    void databaseUniqueConstraintRejectsSecondLoanForApplication() {
        ApprovedFixture fixture = approvedFixture();
        loanExecutionService.executeLoan(fixture.application().getId(), "First execution");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO loan "
                        + "(application_id, principal, outstanding_balance, interest_rate, executed_at) "
                        + "VALUES (?, 20000000.00, 20000000.00, 3.2500, ?)",
                fixture.application().getId(), Timestamp.from(NOW)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void historyFailureRollsBackLoanAndApplicationStatus() {
        ApprovedFixture fixture = approvedFixture();
        Long applicationId = fixture.application().getId();
        int historyCount = statusHistoryRepository.findByApplicationIdOrderByChangedAt(applicationId).size();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        assertThatThrownBy(() -> loanExecutionService.executeLoan(applicationId, "x".repeat(501)))
                .isInstanceOf(DataIntegrityViolationException.class);

        TestTransaction.start();
        assertThat(loanRepository.findByApplicationId(applicationId)).isEmpty();
        assertThat(applicationRepository.findById(applicationId).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.APPROVED);
        assertThat(statusHistoryRepository.findByApplicationIdOrderByChangedAt(applicationId))
                .hasSize(historyCount);
    }

    @Test
    void concurrentExecutionCreatesExactlyOneLoan() throws Exception {
        ApprovedFixture fixture = approvedFixture();
        Long applicationId = fixture.application().getId();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = List.of(
                    executor.submit(() -> executeConcurrently(applicationId, ready, start)),
                    executor.submit(() -> executeConcurrently(applicationId, ready, start))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long successCount = 0;
            for (Future<Boolean> result : results) {
                if (result.get(20, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }

            TestTransaction.start();
            assertThat(successCount).isEqualTo(1);
            assertThat(loanRepository.findByApplicationId(applicationId)).isPresent();
            assertThat(applicationRepository.findById(applicationId).orElseThrow().getStatus())
                    .isEqualTo(ApplicationStatus.EXECUTED);
            assertThat(statusHistoryRepository.findByApplicationIdOrderByChangedAt(applicationId))
                    .filteredOn(history -> history.getToStatus() == ApplicationStatus.EXECUTED)
                    .hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void flywayCreatedLoanTableAndApplicationUniqueConstraint() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '7' AND success = true",
                Integer.class
        );
        Integer uniqueConstraintCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = 'public' AND table_name = 'loan' "
                        + "AND constraint_name = 'uk_loan_application' AND constraint_type = 'UNIQUE'",
                Integer.class
        );

        assertThat(migrationCount).isEqualTo(1);
        assertThat(uniqueConstraintCount).isEqualTo(1);
    }

    private boolean executeConcurrently(Long applicationId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await(10, TimeUnit.SECONDS);
            loanExecutionService.executeLoan(applicationId, "Concurrent execution");
            return true;
        } catch (RuntimeException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private ApprovedFixture approvedFixture() {
        ReviewFixture fixture = reviewFixture();
        PolicyFinanceApplication approved = workflowService.approve(
                fixture.application().getId(), fixture.reviewer().getId(), "Application approved"
        );
        return new ApprovedFixture(approved, fixture.reviewer());
    }

    private ReviewFixture reviewFixture() {
        int number = SEQUENCE.incrementAndGet();
        Department department = departmentRepository.saveAndFlush(new Department(
                "LOAN-DEPT-" + number, "Loan Review Department", true
        ));
        Employee manager = employeeRepository.saveAndFlush(new Employee(
                "LOAN-MANAGER-" + number, "Test Manager", department, true
        ));
        Employee reviewer = employeeRepository.saveAndFlush(new Employee(
                "LOAN-REVIEWER-" + number, "Test Reviewer", department, true
        ));
        Customer customer = customerRepository.saveAndFlush(new Customer(
                "LOAN-CUSTOMER-" + number, "Test Customer", LocalDate.of(1990, 1, 1),
                new BigDecimal("30000000.00"), 750, "SEOUL", LocalDate.of(2020, 1, 1)
        ));
        FinancialProduct product = productRepository.saveAndFlush(new FinancialProduct(
                "LOAN-PRODUCT-" + number, "Test Loan Product", "Loan execution test product",
                new BigDecimal("50000000.00"), true
        ));
        ProductRuleVersion ruleVersion = ruleVersionRepository.saveAndFlush(new ProductRuleVersion(
                product, 1, Instant.parse("2025-01-01T00:00:00Z"), null,
                new BigDecimal("3.2500"), 24, RepaymentMethod.EQUAL_PAYMENT, true
        ));
        eligibilityRuleRepository.saveAndFlush(new EligibilityRule(
                ruleVersion, EligibilityRuleType.REGION, EligibilityRuleOperator.EQUAL,
                "SEOUL", "Applicant must be in the supported region"
        ));
        PolicyFinanceApplication application = applicationService.createApplication(
                customer.getId(), product.getId(), new BigDecimal("20000000.00")
        );
        applicationService.submitApplication(application.getId());
        workflowService.startEligibilityCheck(application.getId(), "Eligibility evaluation started");
        eligibilityService.evaluate(application.getId());
        assignmentService.assign(
                application.getId(), department.getId(), reviewer.getId(), manager.getId(), "Review assignment"
        );
        workflowService.startReview(application.getId(), reviewer.getId(), "Review started");
        return new ReviewFixture(application, reviewer);
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    private record ReviewFixture(PolicyFinanceApplication application, Employee reviewer) {
    }

    private record ApprovedFixture(PolicyFinanceApplication application, Employee reviewer) {
    }
}
