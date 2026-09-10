package com.gonggong.policyfinance.domain.application;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationStatusTest {

    @Test
    void draftCanTransitionToReceived() {
        assertThatCode(() -> ApplicationStatus.DRAFT.validateTransition(ApplicationStatus.RECEIVED))
                .doesNotThrowAnyException();
    }

    @Test
    void underReviewCanTransitionToApproved() {
        assertThat(ApplicationStatus.UNDER_REVIEW.canTransitionTo(ApplicationStatus.APPROVED)).isTrue();
    }

    @Test
    void underReviewCanTransitionToRejected() {
        assertThat(ApplicationStatus.UNDER_REVIEW.canTransitionTo(ApplicationStatus.REJECTED)).isTrue();
    }

    @Test
    void supplementRequestedCanTransitionBackToUnderReview() {
        assertThat(ApplicationStatus.SUPPLEMENT_REQUESTED.canTransitionTo(ApplicationStatus.UNDER_REVIEW)).isTrue();
    }

    @Test
    void draftCannotTransitionDirectlyToApproved() {
        assertInvalidTransition(ApplicationStatus.DRAFT, ApplicationStatus.APPROVED);
    }

    @Test
    void rejectedCannotTransitionToExecuted() {
        assertInvalidTransition(ApplicationStatus.REJECTED, ApplicationStatus.EXECUTED);
    }

    @Test
    void receivedCannotTransitionToRepaying() {
        assertInvalidTransition(ApplicationStatus.RECEIVED, ApplicationStatus.REPAYING);
    }

    @Test
    void cannotTransitionToNull() {
        assertThat(ApplicationStatus.DRAFT.canTransitionTo(null)).isFalse();
    }

    @Test
    void validatingNullTransitionThrowsDomainException() {
        assertThatThrownBy(() -> ApplicationStatus.DRAFT.validateTransition(null))
                .isInstanceOf(InvalidApplicationStatusTransitionException.class)
                .hasMessageContaining("DRAFT")
                .hasMessageContaining("null");
    }

    @Test
    void onlyDocumentedTransitionsAreAllowed() {
        Map<ApplicationStatus, Set<ApplicationStatus>> expectedTransitions = Map.of(
                ApplicationStatus.DRAFT, Set.of(ApplicationStatus.RECEIVED),
                ApplicationStatus.RECEIVED, Set.of(ApplicationStatus.ELIGIBILITY_CHECK),
                ApplicationStatus.ELIGIBILITY_CHECK, Set.of(ApplicationStatus.UNDER_REVIEW),
                ApplicationStatus.UNDER_REVIEW, Set.of(
                        ApplicationStatus.SUPPLEMENT_REQUESTED,
                        ApplicationStatus.APPROVED,
                        ApplicationStatus.REJECTED
                ),
                ApplicationStatus.SUPPLEMENT_REQUESTED, Set.of(ApplicationStatus.UNDER_REVIEW),
                ApplicationStatus.APPROVED, Set.of(ApplicationStatus.EXECUTED),
                ApplicationStatus.EXECUTED, Set.of(ApplicationStatus.REPAYING),
                ApplicationStatus.REPAYING, Set.of(ApplicationStatus.REPAID),
                ApplicationStatus.REPAID, Set.of(ApplicationStatus.CLOSED)
        );

        for (ApplicationStatus current : ApplicationStatus.values()) {
            for (ApplicationStatus next : ApplicationStatus.values()) {
                boolean expected = expectedTransitions.getOrDefault(current, Set.of()).contains(next);

                assertThat(current.canTransitionTo(next))
                        .as("transition from %s to %s", current, next)
                        .isEqualTo(expected);
            }
        }
    }

    private void assertInvalidTransition(ApplicationStatus current, ApplicationStatus next) {
        assertThatThrownBy(() -> current.validateTransition(next))
                .isInstanceOf(InvalidApplicationStatusTransitionException.class)
                .hasMessageContaining(current.name())
                .hasMessageContaining(next.name());
    }
}
