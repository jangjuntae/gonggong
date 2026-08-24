package com.gonggong.policyfinance.domain.application;

import java.util.List;

public record EligibilityEvaluation(boolean eligible, List<ScreeningResult> results) {

    public EligibilityEvaluation {
        results = List.copyOf(results);
    }
}
