package com.gonggong.policyfinance.domain.application;

import com.gonggong.policyfinance.domain.customer.Customer;
import com.gonggong.policyfinance.domain.customer.CustomerRepository;
import com.gonggong.policyfinance.domain.organization.Department;
import com.gonggong.policyfinance.domain.organization.DepartmentRepository;
import com.gonggong.policyfinance.domain.organization.Employee;
import com.gonggong.policyfinance.domain.organization.EmployeeRepository;
import com.gonggong.policyfinance.domain.product.FinancialProduct;
import com.gonggong.policyfinance.domain.product.FinancialProductRepository;
import com.gonggong.policyfinance.domain.product.EligibilityRule;
import com.gonggong.policyfinance.domain.product.EligibilityRuleOperator;
import com.gonggong.policyfinance.domain.product.EligibilityRuleRepository;
import com.gonggong.policyfinance.domain.product.EligibilityRuleType;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Transactional
class ApplicationAssignmentReviewIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired ApplicationService applicationService;
    @Autowired ApplicationAssignmentService assignmentService;
    @Autowired ApplicationWorkflowService workflowService;
    @Autowired EligibilityService eligibilityService;
    @Autowired CustomerRepository customerRepository;
    @Autowired FinancialProductRepository productRepository;
    @Autowired ProductRuleVersionRepository ruleVersionRepository;
    @Autowired EligibilityRuleRepository eligibilityRuleRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired ApplicationAssignmentRepository assignmentRepository;
    @Autowired PolicyFinanceApplicationRepository applicationRepository;
    @Autowired StatusHistoryRepository statusHistoryRepository;
    @Autowired ApplicationReviewRepository reviewRepository;
    @Autowired ScreeningResultRepository screeningResultRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void assignsActiveEmployeeAndReturnsCurrentAssignment() {
        TestFixture fixture = fixture();

        ApplicationAssignment assignment = assignmentService.assign(
                fixture.application().getId(), fixture.department().getId(),
                fixture.reviewer().getId(), fixture.manager().getId(), "Initial assignment"
        );

        assertThat(assignment.isActive()).isTrue();
        assertThat(assignment.getReleasedAt()).isNull();
        assertThat(assignmentRepository.findByApplicationIdAndActiveTrue(fixture.application().getId()))
                .hasValueSatisfying(current -> {
                    assertThat(current.getEmployee().getId()).isEqualTo(fixture.reviewer().getId());
                    assertThat(current.getAssignedBy().getId()).isEqualTo(fixture.manager().getId());
                });
    }

    @Test
    void rejectsEmployeeFromAnotherDepartmentAndInactiveEmployee() {
        TestFixture fixture = fixture();
        Department otherDepartment = saveDepartment("OTHER");
        Employee otherEmployee = saveEmployee(otherDepartment, true, "OTHER");
        Employee inactiveEmployee = saveEmployee(fixture.department(), false, "INACTIVE");

        assertThatThrownBy(() -> assignmentService.assign(
                fixture.application().getId(), fixture.department().getId(), otherEmployee.getId(),
                fixture.manager().getId(), "Wrong department"
        )).isInstanceOf(ApplicationBusinessException.class)
                .hasMessageContaining("does not belong");

        assertThatThrownBy(() -> assignmentService.assign(
                fixture.application().getId(), fixture.department().getId(), inactiveEmployee.getId(),
                fixture.manager().getId(), "Inactive employee"
        )).isInstanceOf(ApplicationBusinessException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void reassignsWithoutDeletingPreviousAssignment() {
        TestFixture fixture = fixture();
        Employee nextReviewer = saveEmployee(fixture.department(), true, "NEXT");
        ApplicationAssignment previous = assignmentService.assign(
                fixture.application().getId(), fixture.department().getId(), fixture.reviewer().getId(),
                fixture.manager().getId(), "Initial assignment"
        );

        ApplicationAssignment current = assignmentService.assign(
                fixture.application().getId(), fixture.department().getId(), nextReviewer.getId(),
                fixture.manager().getId(), "Workload reallocation"
        );
        List<ApplicationAssignment> history = assignmentRepository
                .findByApplicationIdOrderByAssignedAt(fixture.application().getId());

        assertThat(history).hasSize(2);
        assertThat(previous.isActive()).isFalse();
        assertThat(previous.getReleasedAt()).isEqualTo(NOW);
        assertThat(current.isActive()).isTrue();
        assertThat(assignmentRepository.findByApplicationIdAndActiveTrue(fixture.application().getId()))
                .hasValueSatisfying(found -> assertThat(found.getEmployee().getId()).isEqualTo(nextReviewer.getId()));
    }

    @Test
    void databaseRejectsTwoActiveAssignmentsForSameApplication() {
        TestFixture fixture = fixture();
        assignmentService.assign(
                fixture.application().getId(), fixture.department().getId(), fixture.reviewer().getId(),
                fixture.manager().getId(), "Initial assignment"
        );
        Employee nextReviewer = saveEmployee(fixture.department(), true, "DUPLICATE");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO application_assignment "
                        + "(application_id, department_id, employee_id, assigned_by_employee_id, "
                        + "assigned_at, active, reason) VALUES (?, ?, ?, ?, ?, true, ?)",
                fixture.application().getId(), fixture.department().getId(), nextReviewer.getId(),
                fixture.manager().getId(), Timestamp.from(NOW), "Duplicate active assignment"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsEmployeeAndDepartmentMismatch() {
        TestFixture fixture = fixture();
        Department otherDepartment = saveDepartment("FK-OTHER");
        Employee otherEmployee = saveEmployee(otherDepartment, true, "FK-OTHER");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO application_assignment "
                        + "(application_id, department_id, employee_id, assigned_by_employee_id, "
                        + "assigned_at, active, reason) VALUES (?, ?, ?, ?, ?, true, ?)",
                fixture.application().getId(), fixture.department().getId(), otherEmployee.getId(),
                fixture.manager().getId(), Timestamp.from(NOW), "Invalid department pair"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void failedReassignmentRollsBackReleaseOfCurrentAssignment() {
        TestFixture fixture = fixture();
        assignmentService.assign(
                fixture.application().getId(), fixture.department().getId(), fixture.reviewer().getId(),
                fixture.manager().getId(), "Initial assignment"
        );
        Employee nextReviewer = saveEmployee(fixture.department(), true, "ROLLBACK");
        Long applicationId = fixture.application().getId();
        Long departmentId = fixture.department().getId();
        Long managerId = fixture.manager().getId();
        Long nextReviewerId = nextReviewer.getId();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        assertThatThrownBy(() -> assignmentService.assign(
                applicationId, departmentId, nextReviewerId, managerId, "x".repeat(501)
        )).isInstanceOf(DataIntegrityViolationException.class);

        TestTransaction.start();
        assertThat(assignmentRepository.findByApplicationIdOrderByAssignedAt(applicationId)).singleElement()
                .satisfies(current -> {
                    assertThat(current.isActive()).isTrue();
                    assertThat(current.getReleasedAt()).isNull();
                });
    }

    @Test
    void assignedReviewerCompletesSupplementAndApprovalWorkflowWithHistory() {
        TestFixture fixture = fixture();
        prepareForReview(fixture);

        workflowService.startReview(fixture.application().getId(), fixture.reviewer().getId(), "Review started");
        workflowService.requestSupplement(
                fixture.application().getId(), fixture.reviewer().getId(), "Income document required"
        );
        workflowService.resumeReview(
                fixture.application().getId(), fixture.reviewer().getId(), "Supplement received"
        );
        PolicyFinanceApplication approved = workflowService.approve(
                fixture.application().getId(), fixture.reviewer().getId(), "Requirements satisfied"
        );

        assertThat(approved.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(statusHistoryRepository.findByApplicationIdOrderByChangedAt(approved.getId()))
                .extracting(StatusHistory::getToStatus)
                .containsExactly(
                        ApplicationStatus.RECEIVED,
                        ApplicationStatus.ELIGIBILITY_CHECK,
                        ApplicationStatus.UNDER_REVIEW,
                        ApplicationStatus.SUPPLEMENT_REQUESTED,
                        ApplicationStatus.UNDER_REVIEW,
                        ApplicationStatus.APPROVED
                );
        assertThat(reviewRepository.findByApplicationIdOrderByCreatedAt(approved.getId()))
                .extracting(ApplicationReview::getAction)
                .containsExactly(
                        ApplicationReviewAction.REVIEW_STARTED,
                        ApplicationReviewAction.SUPPLEMENT_REQUESTED,
                        ApplicationReviewAction.REVIEW_RESUMED,
                        ApplicationReviewAction.APPROVED
                );
        StatusHistory eligibilityHistory = statusHistoryRepository
                .findByApplicationIdOrderByChangedAt(approved.getId()).get(1);
        assertThat(eligibilityHistory.getChangedBy()).isNull();
        assertThat(eligibilityHistory.getChangedAt()).isEqualTo(NOW);

        StatusHistory approvalHistory = statusHistoryRepository
                .findByApplicationIdOrderByChangedAt(approved.getId()).get(5);
        assertThat(approvalHistory.getFromStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        assertThat(approvalHistory.getToStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(approvalHistory.getChangedBy().getId()).isEqualTo(fixture.reviewer().getId());
        assertThat(approvalHistory.getReason()).isEqualTo("Requirements satisfied");
        assertThat(approvalHistory.getChangedAt()).isEqualTo(NOW);

        ApplicationReview approvalReview = reviewRepository
                .findByApplicationIdOrderByCreatedAt(approved.getId()).get(3);
        assertThat(approvalReview.getEmployee().getId()).isEqualTo(fixture.reviewer().getId());
        assertThat(approvalReview.getAction()).isEqualTo(ApplicationReviewAction.APPROVED);
        assertThat(approvalReview.getComment()).isEqualTo("Requirements satisfied");
        assertThat(approvalReview.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsReviewStartWhenEligibilityEvaluationWasNotExecuted() {
        TestFixture fixture = fixture();
        applicationService.submitApplication(fixture.application().getId());
        workflowService.startEligibilityCheck(fixture.application().getId(), "Eligibility evaluation started");
        assignmentService.assign(
                fixture.application().getId(), fixture.department().getId(), fixture.reviewer().getId(),
                fixture.manager().getId(), "Initial assignment"
        );

        assertThatThrownBy(() -> workflowService.startReview(
                fixture.application().getId(), fixture.reviewer().getId(), "Review started"
        )).isInstanceOf(ApplicationBusinessException.class)
                .hasMessageContaining("Eligibility evaluation is not complete");

        assertThat(applicationRepository.findById(fixture.application().getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.ELIGIBILITY_CHECK);
        assertThat(statusHistoryRepository.findByApplicationIdOrderByChangedAt(fixture.application().getId()))
                .extracting(StatusHistory::getToStatus)
                .containsExactly(ApplicationStatus.RECEIVED, ApplicationStatus.ELIGIBILITY_CHECK);
        assertThat(reviewRepository.findByApplicationIdOrderByCreatedAt(fixture.application().getId())).isEmpty();
    }

    @Test
    void startsReviewAfterEveryEligibilityRuleWasEvaluated() {
        TestFixture fixture = fixture();
        applicationService.submitApplication(fixture.application().getId());
        workflowService.startEligibilityCheck(fixture.application().getId(), "Eligibility evaluation started");
        EligibilityEvaluation evaluation = eligibilityService.evaluate(fixture.application().getId());
        assignmentService.assign(
                fixture.application().getId(), fixture.department().getId(), fixture.reviewer().getId(),
                fixture.manager().getId(), "Initial assignment"
        );

        PolicyFinanceApplication reviewing = workflowService.startReview(
                fixture.application().getId(), fixture.reviewer().getId(), "Review started"
        );

        assertThat(evaluation.results()).hasSize(1);
        assertThat(reviewing.getStatus()).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        assertThat(statusHistoryRepository.findByApplicationIdOrderByChangedAt(reviewing.getId()))
                .extracting(StatusHistory::getToStatus)
                .containsExactly(
                        ApplicationStatus.RECEIVED,
                        ApplicationStatus.ELIGIBILITY_CHECK,
                        ApplicationStatus.UNDER_REVIEW
                );
        assertThat(reviewRepository.findByApplicationIdOrderByCreatedAt(reviewing.getId()))
                .extracting(ApplicationReview::getAction)
                .containsExactly(ApplicationReviewAction.REVIEW_STARTED);
    }

    @Test
    void rejectsReviewStartWhenOnlySomeEligibilityRulesWereEvaluated() {
        TestFixture fixture = fixture();
        eligibilityRuleRepository.saveAndFlush(new EligibilityRule(
                fixture.application().getProductRuleVersion(),
                EligibilityRuleType.CREDIT_SCORE,
                EligibilityRuleOperator.GREATER_THAN_OR_EQUAL,
                "700",
                "Applicant must meet the minimum credit score"
        ));
        applicationService.submitApplication(fixture.application().getId());
        workflowService.startEligibilityCheck(fixture.application().getId(), "Eligibility evaluation started");
        EligibilityRule onlyEvaluatedRule = eligibilityRuleRepository
                .findByProductRuleVersionId(fixture.application().getProductRuleVersion().getId()).get(0);
        screeningResultRepository.saveAndFlush(new ScreeningResult(
                fixture.application(), onlyEvaluatedRule, true, "Partial result", NOW
        ));
        assignmentService.assign(
                fixture.application().getId(), fixture.department().getId(), fixture.reviewer().getId(),
                fixture.manager().getId(), "Initial assignment"
        );

        assertThatThrownBy(() -> workflowService.startReview(
                fixture.application().getId(), fixture.reviewer().getId(), "Review started"
        )).isInstanceOf(ApplicationBusinessException.class)
                .hasMessageContaining("Eligibility evaluation is not complete");
        assertThat(applicationRepository.findById(fixture.application().getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.ELIGIBILITY_CHECK);
    }

    @Test
    void assignedReviewerCanRejectApplication() {
        TestFixture fixture = fixture();
        prepareForReview(fixture);
        workflowService.startReview(fixture.application().getId(), fixture.reviewer().getId(), "Review started");

        PolicyFinanceApplication rejected = workflowService.reject(
                fixture.application().getId(), fixture.reviewer().getId(), "Eligibility evidence insufficient"
        );

        assertThat(rejected.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(reviewRepository.findByApplicationIdOrderByCreatedAt(rejected.getId()))
                .extracting(ApplicationReview::getAction)
                .containsExactly(ApplicationReviewAction.REVIEW_STARTED, ApplicationReviewAction.REJECTED);
    }

    @Test
    void rejectsWorkflowByNonAssigneeOrWithoutAssignment() {
        TestFixture fixture = fixture();
        applicationService.submitApplication(fixture.application().getId());
        workflowService.startEligibilityCheck(fixture.application().getId(), "Eligibility evaluation started");
        eligibilityService.evaluate(fixture.application().getId());

        assertThatThrownBy(() -> workflowService.startReview(
                fixture.application().getId(), fixture.reviewer().getId(), "No assignment"
        )).isInstanceOf(ApplicationBusinessException.class)
                .hasMessageContaining("no active assignment");

        assignmentService.assign(
                fixture.application().getId(), fixture.department().getId(), fixture.reviewer().getId(),
                fixture.manager().getId(), "Initial assignment"
        );
        Employee otherReviewer = saveEmployee(fixture.department(), true, "UNASSIGNED");
        assertThatThrownBy(() -> workflowService.startReview(
                fixture.application().getId(), otherReviewer.getId(), "Unauthorized review"
        )).isInstanceOf(ApplicationBusinessException.class)
                .hasMessageContaining("current assignee");
    }

    @Test
    void invalidTransitionDoesNotCreateHistoryOrReview() {
        TestFixture fixture = fixture();
        applicationService.submitApplication(fixture.application().getId());
        assignmentService.assign(
                fixture.application().getId(), fixture.department().getId(), fixture.reviewer().getId(),
                fixture.manager().getId(), "Initial assignment"
        );

        assertThatThrownBy(() -> workflowService.approve(
                fixture.application().getId(), fixture.reviewer().getId(), "Premature approval"
        )).isInstanceOf(InvalidApplicationStatusTransitionException.class);

        assertThat(statusHistoryRepository.findByApplicationIdOrderByChangedAt(fixture.application().getId()))
                .extracting(StatusHistory::getToStatus)
                .containsExactly(ApplicationStatus.RECEIVED);
        assertThat(reviewRepository.findByApplicationIdOrderByCreatedAt(fixture.application().getId())).isEmpty();
    }

    @Test
    void failedHistoryInsertRollsBackApplicationStatus() {
        TestFixture fixture = fixture();
        applicationService.submitApplication(fixture.application().getId());
        Long applicationId = fixture.application().getId();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        assertThatThrownBy(() -> workflowService.startEligibilityCheck(applicationId, "x".repeat(501)))
                .isInstanceOf(DataIntegrityViolationException.class);

        TestTransaction.start();
        assertThat(applicationRepository.findById(applicationId).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.RECEIVED);
        assertThat(statusHistoryRepository.findByApplicationIdOrderByChangedAt(applicationId))
                .extracting(StatusHistory::getToStatus)
                .containsExactly(ApplicationStatus.RECEIVED);
    }

    @Test
    void flywayAppliedWorkflowMigrationAndPartialUniqueIndex() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '6' AND success = true",
                Integer.class
        );
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name IN ('department', 'employee', 'application_assignment', "
                        + "'status_history', 'application_review')",
                Integer.class
        );
        String indexPredicate = jdbcTemplate.queryForObject(
                "SELECT pg_get_expr(i.indpred, i.indrelid) FROM pg_index i "
                        + "JOIN pg_class c ON c.oid = i.indexrelid "
                        + "WHERE c.relname = 'uk_application_assignment_one_active'",
                String.class
        );

        assertThat(migrationCount).isEqualTo(1);
        assertThat(tableCount).isEqualTo(5);
        assertThat(indexPredicate).contains("active = true");
    }

    private void prepareForReview(TestFixture fixture) {
        applicationService.submitApplication(fixture.application().getId());
        workflowService.startEligibilityCheck(fixture.application().getId(), "Eligibility evaluation started");
        eligibilityService.evaluate(fixture.application().getId());
        assignmentService.assign(
                fixture.application().getId(), fixture.department().getId(), fixture.reviewer().getId(),
                fixture.manager().getId(), "Initial assignment"
        );
    }

    private TestFixture fixture() {
        int number = SEQUENCE.incrementAndGet();
        Department department = saveDepartment("DEPT-" + number);
        Employee manager = saveEmployee(department, true, "MANAGER-" + number);
        Employee reviewer = saveEmployee(department, true, "REVIEWER-" + number);
        Customer customer = customerRepository.saveAndFlush(new Customer(
                "CUSTOMER-" + number, "Test Customer", LocalDate.of(1990, 1, 1),
                new BigDecimal("30000000.00"), 750, "SEOUL", LocalDate.of(2020, 1, 1)
        ));
        FinancialProduct product = productRepository.saveAndFlush(new FinancialProduct(
                "PRODUCT-" + number, "Test Product", "Assignment review test product",
                new BigDecimal("50000000.00"), true
        ));
        ProductRuleVersion ruleVersion = ruleVersionRepository.saveAndFlush(new ProductRuleVersion(
                product, 1, Instant.parse("2025-01-01T00:00:00Z"), null,
                new BigDecimal("3.2500"), 24, RepaymentMethod.EQUAL_PAYMENT, true
        ));
        eligibilityRuleRepository.saveAndFlush(new EligibilityRule(
                ruleVersion,
                EligibilityRuleType.REGION,
                EligibilityRuleOperator.EQUAL,
                "SEOUL",
                "Applicant must be located in the supported region"
        ));
        PolicyFinanceApplication application = applicationService.createApplication(
                customer.getId(), product.getId(), new BigDecimal("20000000.00")
        );
        return new TestFixture(application, department, manager, reviewer);
    }

    private Department saveDepartment(String suffix) {
        return departmentRepository.saveAndFlush(new Department(
                "CODE-" + suffix + "-" + SEQUENCE.incrementAndGet(), "Review Department", true
        ));
    }

    private Employee saveEmployee(Department department, boolean active, String suffix) {
        return employeeRepository.saveAndFlush(new Employee(
                "EMP-" + suffix + "-" + SEQUENCE.incrementAndGet(), "Test Employee", department, active
        ));
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    private record TestFixture(
            PolicyFinanceApplication application,
            Department department,
            Employee manager,
            Employee reviewer
    ) {
    }
}
