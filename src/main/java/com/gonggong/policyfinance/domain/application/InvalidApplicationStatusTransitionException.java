package com.gonggong.policyfinance.domain.application;

public class InvalidApplicationStatusTransitionException extends RuntimeException {

    public InvalidApplicationStatusTransitionException(ApplicationStatus current, ApplicationStatus next) {
        super("Application status transition is not allowed: " + current + " -> " + next);
    }
}
