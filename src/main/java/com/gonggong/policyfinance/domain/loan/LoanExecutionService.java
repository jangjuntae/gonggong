package com.gonggong.policyfinance.domain.loan;

import com.gonggong.policyfinance.domain.application.ApplicationBusinessException;
import com.gonggong.policyfinance.domain.application.ApplicationStatus;
import com.gonggong.policyfinance.domain.application.PolicyFinanceApplication;
import com.gonggong.policyfinance.domain.application.PolicyFinanceApplicationRepository;
import com.gonggong.policyfinance.domain.application.StatusHistory;
import com.gonggong.policyfinance.domain.application.StatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class LoanExecutionService {

    private final PolicyFinanceApplicationRepository applicationRepository;
    private final LoanRepository loanRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final Clock clock;

    public LoanExecutionService(
            PolicyFinanceApplicationRepository applicationRepository,
            LoanRepository loanRepository,
            StatusHistoryRepository statusHistoryRepository,
            Clock clock
    ) {
        this.applicationRepository = applicationRepository;
        this.loanRepository = loanRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.clock = clock;
    }

    @Transactional
    public Loan executeLoan(Long applicationId, String reason) {
        validateReason(reason);
        PolicyFinanceApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationBusinessException("Application not found: " + applicationId));

        if (loanRepository.existsByApplicationId(applicationId)) {
            throw new ApplicationBusinessException("Loan has already been executed: " + applicationId);
        }
        if (application.getStatus() != ApplicationStatus.APPROVED) {
            throw new ApplicationBusinessException("Only an approved application can be executed: " + applicationId);
        }

        Instant executedAt = clock.instant();
        Loan loan = loanRepository.save(new Loan(
                application,
                application.getRequestedAmount(),
                application.getProductRuleVersion().getInterestRate(),
                executedAt
        ));
        ApplicationStatus previousStatus = application.getStatus();
        application.execute();
        statusHistoryRepository.save(new StatusHistory(
                application,
                previousStatus,
                application.getStatus(),
                null,
                executedAt,
                reason
        ));

        loanRepository.flush();
        applicationRepository.flush();
        statusHistoryRepository.flush();
        return loan;
    }

    private void validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApplicationBusinessException("Loan execution reason is required");
        }
    }
}
