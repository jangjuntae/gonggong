package com.gonggong.policyfinance.domain.application;

import com.gonggong.policyfinance.domain.organization.Employee;
import com.gonggong.policyfinance.domain.organization.EmployeeRepository;
import com.gonggong.policyfinance.domain.product.EligibilityRule;
import com.gonggong.policyfinance.domain.product.EligibilityRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class ApplicationWorkflowService {

    private final PolicyFinanceApplicationRepository applicationRepository;
    private final EmployeeRepository employeeRepository;
    private final ApplicationAssignmentRepository assignmentRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final ApplicationReviewRepository reviewRepository;
    private final EligibilityRuleRepository eligibilityRuleRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final Clock clock;

    public ApplicationWorkflowService(
            PolicyFinanceApplicationRepository applicationRepository,
            EmployeeRepository employeeRepository,
            ApplicationAssignmentRepository assignmentRepository,
            StatusHistoryRepository statusHistoryRepository,
            ApplicationReviewRepository reviewRepository,
            EligibilityRuleRepository eligibilityRuleRepository,
            ScreeningResultRepository screeningResultRepository,
            Clock clock
    ) {
        this.applicationRepository = applicationRepository;
        this.employeeRepository = employeeRepository;
        this.assignmentRepository = assignmentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.reviewRepository = reviewRepository;
        this.eligibilityRuleRepository = eligibilityRuleRepository;
        this.screeningResultRepository = screeningResultRepository;
        this.clock = clock;
    }

    @Transactional
    public PolicyFinanceApplication startEligibilityCheck(Long applicationId, String reason) {
        PolicyFinanceApplication application = findApplication(applicationId);
        return transition(application, null, reason, PolicyFinanceApplication::startEligibilityCheck, null);
    }

    @Transactional
    public PolicyFinanceApplication startReview(Long applicationId, Long employeeId, String comment) {
        validateEligibilityCompleted(applicationId);
        return employeeTransition(applicationId, employeeId, comment,
                PolicyFinanceApplication::startReview, ApplicationReviewAction.REVIEW_STARTED);
    }

    @Transactional
    public PolicyFinanceApplication requestSupplement(Long applicationId, Long employeeId, String comment) {
        return employeeTransition(applicationId, employeeId, comment,
                PolicyFinanceApplication::requestSupplement, ApplicationReviewAction.SUPPLEMENT_REQUESTED);
    }

    @Transactional
    public PolicyFinanceApplication resumeReview(Long applicationId, Long employeeId, String comment) {
        return employeeTransition(applicationId, employeeId, comment,
                PolicyFinanceApplication::resumeReview, ApplicationReviewAction.REVIEW_RESUMED);
    }

    @Transactional
    public PolicyFinanceApplication approve(Long applicationId, Long employeeId, String comment) {
        return employeeTransition(applicationId, employeeId, comment,
                PolicyFinanceApplication::approve, ApplicationReviewAction.APPROVED);
    }

    @Transactional
    public PolicyFinanceApplication reject(Long applicationId, Long employeeId, String comment) {
        return employeeTransition(applicationId, employeeId, comment,
                PolicyFinanceApplication::reject, ApplicationReviewAction.REJECTED);
    }

    private PolicyFinanceApplication employeeTransition(
            Long applicationId,
            Long employeeId,
            String comment,
            Consumer<PolicyFinanceApplication> stateChange,
            ApplicationReviewAction action
    ) {
        PolicyFinanceApplication application = findApplication(applicationId);
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ApplicationBusinessException("Employee not found: " + employeeId));
        if (!employee.isActive()) {
            throw new ApplicationBusinessException("Employee is not active: " + employeeId);
        }
        ApplicationAssignment assignment = assignmentRepository.findByApplicationIdAndActiveTrue(applicationId)
                .orElseThrow(() -> new ApplicationBusinessException(
                        "Application has no active assignment: " + applicationId
                ));
        if (!assignment.getEmployee().getId().equals(employeeId)) {
            throw new ApplicationBusinessException("Only the current assignee can review the application");
        }
        return transition(application, employee, comment, stateChange, action);
    }

    private PolicyFinanceApplication transition(
            PolicyFinanceApplication application,
            Employee employee,
            String reason,
            Consumer<PolicyFinanceApplication> stateChange,
            ApplicationReviewAction action
    ) {
        validateReason(reason);
        ApplicationStatus previousStatus = application.getStatus();
        Instant changedAt = clock.instant();
        stateChange.accept(application);
        statusHistoryRepository.save(new StatusHistory(
                application, previousStatus, application.getStatus(), employee, changedAt, reason
        ));
        if (action != null) {
            reviewRepository.save(new ApplicationReview(application, employee, action, reason, changedAt));
        }
        applicationRepository.flush();
        statusHistoryRepository.flush();
        reviewRepository.flush();
        return application;
    }

    private PolicyFinanceApplication findApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationBusinessException("Application not found: " + applicationId));
    }

    private void validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApplicationBusinessException("Status change reason is required");
        }
    }

    private void validateEligibilityCompleted(Long applicationId) {
        PolicyFinanceApplication application = findApplication(applicationId);
        Set<Long> expectedRuleIds = eligibilityRuleRepository
                .findByProductRuleVersionId(application.getProductRuleVersion().getId())
                .stream()
                .map(EligibilityRule::getId)
                .collect(Collectors.toSet());
        Set<Long> evaluatedRuleIds = screeningResultRepository
                .findByApplicationIdOrderById(applicationId)
                .stream()
                .map(ScreeningResult::getEligibilityRule)
                .map(EligibilityRule::getId)
                .collect(Collectors.toSet());

        if (expectedRuleIds.isEmpty() || !evaluatedRuleIds.equals(expectedRuleIds)) {
            throw new ApplicationBusinessException(
                    "Eligibility evaluation is not complete for application: " + applicationId
            );
        }
    }
}
