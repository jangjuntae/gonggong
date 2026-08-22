package com.gonggong.policyfinance.domain.application;

import java.util.Map;
import java.util.Set;

public enum ApplicationStatus {

    DRAFT,
    RECEIVED,
    ELIGIBILITY_CHECK,
    UNDER_REVIEW,
    SUPPLEMENT_REQUESTED,
    APPROVED,
    REJECTED,
    EXECUTED,
    REPAYING,
    REPAID,
    CLOSED;

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS = Map.of(
            DRAFT, Set.of(RECEIVED),
            RECEIVED, Set.of(ELIGIBILITY_CHECK),
            ELIGIBILITY_CHECK, Set.of(UNDER_REVIEW),
            UNDER_REVIEW, Set.of(SUPPLEMENT_REQUESTED, APPROVED, REJECTED),
            SUPPLEMENT_REQUESTED, Set.of(UNDER_REVIEW),
            APPROVED, Set.of(EXECUTED),
            EXECUTED, Set.of(REPAYING),
            REPAYING, Set.of(REPAID),
            REPAID, Set.of(CLOSED)
    );

    public boolean canTransitionTo(ApplicationStatus next) {
        return next != null && ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }

    public void validateTransition(ApplicationStatus next) {
        if (!canTransitionTo(next)) {
            throw new InvalidApplicationStatusTransitionException(this, next);
        }
    }
}
