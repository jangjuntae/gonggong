package com.gonggong.policyfinance.domain.application;

public class ApplicationBusinessException extends RuntimeException {

    public ApplicationBusinessException(String message) {
        super(message);
    }
}
