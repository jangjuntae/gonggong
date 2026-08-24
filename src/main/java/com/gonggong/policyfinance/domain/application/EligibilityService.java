package com.gonggong.policyfinance.domain.application;

import com.gonggong.policyfinance.domain.customer.Customer;
import com.gonggong.policyfinance.domain.product.EligibilityRule;
import com.gonggong.policyfinance.domain.product.EligibilityRuleOperator;
import com.gonggong.policyfinance.domain.product.EligibilityRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class EligibilityService {

    private final PolicyFinanceApplicationRepository applicationRepository;
    private final EligibilityRuleRepository eligibilityRuleRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final Clock clock;

    public EligibilityService(
            PolicyFinanceApplicationRepository applicationRepository,
            EligibilityRuleRepository eligibilityRuleRepository,
            ScreeningResultRepository screeningResultRepository,
            Clock clock
    ) {
        this.applicationRepository = applicationRepository;
        this.eligibilityRuleRepository = eligibilityRuleRepository;
        this.screeningResultRepository = screeningResultRepository;
        this.clock = clock;
    }

    @Transactional
    public EligibilityEvaluation evaluate(Long applicationId) {
        PolicyFinanceApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationBusinessException("Application not found: " + applicationId));

        if (application.getSubmittedAt() == null) {
            throw new ApplicationBusinessException("Application must be submitted before eligibility evaluation");
        }

        List<EligibilityRule> rules = eligibilityRuleRepository.findByProductRuleVersionId(
                application.getProductRuleVersion().getId()
        );
        if (rules.isEmpty()) {
            throw new ApplicationBusinessException(
                    "No eligibility rules configured for product rule version: "
                            + application.getProductRuleVersion().getId()
            );
        }
        Instant checkedAt = clock.instant();
        List<ScreeningResult> results = new ArrayList<>();

        for (EligibilityRule rule : rules) {
            boolean passed = evaluateRule(rule, application.getCustomer(), application.getSubmittedAt());
            String reason = rule.getRuleType().name() + " rule " + (passed ? "passed" : "failed");
            results.add(new ScreeningResult(application, rule, passed, reason, checkedAt));
        }

        List<ScreeningResult> savedResults = screeningResultRepository.saveAll(results);
        boolean eligible = savedResults.stream().allMatch(ScreeningResult::isPassed);
        return new EligibilityEvaluation(eligible, savedResults);
    }

    private boolean evaluateRule(EligibilityRule rule, Customer customer, Instant submittedAt) {
        try {
            return switch (rule.getRuleType()) {
                case AGE -> evaluateInteger(
                        Period.between(customer.getBirthDate(), submittedDate(submittedAt)).getYears(),
                        rule
                );
                case INCOME -> evaluateDecimal(customer.getAnnualIncome(), rule);
                case CREDIT_SCORE -> evaluateInteger(customer.getCreditScore(), rule);
                case REGION -> evaluateRegion(customer.getRegion(), rule);
                case BUSINESS_PERIOD -> evaluateBusinessPeriod(customer.getBusinessStartDate(), submittedAt, rule);
            };
        } catch (NumberFormatException exception) {
            throw new ApplicationBusinessException(
                    "Invalid value for " + rule.getRuleType() + " rule: " + rule.getValue()
            );
        }
    }

    private boolean evaluateInteger(int actual, EligibilityRule rule) {
        return switch (rule.getOperator()) {
            case GREATER_THAN_OR_EQUAL -> actual >= Integer.parseInt(rule.getValue().trim());
            case LESS_THAN_OR_EQUAL -> actual <= Integer.parseInt(rule.getValue().trim());
            case BETWEEN -> {
                String[] bounds = bounds(rule);
                int minimum = Integer.parseInt(bounds[0]);
                int maximum = Integer.parseInt(bounds[1]);
                yield actual >= minimum && actual <= maximum;
            }
            default -> throw unsupportedOperator(rule);
        };
    }

    private boolean evaluateDecimal(BigDecimal actual, EligibilityRule rule) {
        return switch (rule.getOperator()) {
            case GREATER_THAN_OR_EQUAL -> actual.compareTo(decimal(rule.getValue())) >= 0;
            case LESS_THAN_OR_EQUAL -> actual.compareTo(decimal(rule.getValue())) <= 0;
            case BETWEEN -> {
                String[] bounds = bounds(rule);
                BigDecimal minimum = decimal(bounds[0]);
                BigDecimal maximum = decimal(bounds[1]);
                yield actual.compareTo(minimum) >= 0 && actual.compareTo(maximum) <= 0;
            }
            default -> throw unsupportedOperator(rule);
        };
    }

    private boolean evaluateRegion(String actual, EligibilityRule rule) {
        if (rule.getOperator() != EligibilityRuleOperator.EQUAL) {
            throw unsupportedOperator(rule);
        }
        return actual.equalsIgnoreCase(rule.getValue().trim());
    }

    private boolean evaluateBusinessPeriod(
            LocalDate businessStartDate,
            Instant submittedAt,
            EligibilityRule rule
    ) {
        if (rule.getOperator() != EligibilityRuleOperator.GREATER_THAN_OR_EQUAL) {
            throw unsupportedOperator(rule);
        }
        if (businessStartDate == null) {
            return false;
        }
        long months = ChronoUnit.MONTHS.between(businessStartDate, submittedDate(submittedAt));
        return months >= Long.parseLong(rule.getValue().trim());
    }

    private String[] bounds(EligibilityRule rule) {
        String[] bounds = rule.getValue().split(",", -1);
        if (bounds.length != 2 || bounds[0].isBlank() || bounds[1].isBlank()) {
            throw new ApplicationBusinessException(
                    "BETWEEN rule value must use 'minimum,maximum' format: " + rule.getValue()
            );
        }
        return new String[]{bounds[0].trim(), bounds[1].trim()};
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value.trim());
    }

    private LocalDate submittedDate(Instant submittedAt) {
        return submittedAt.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private ApplicationBusinessException unsupportedOperator(EligibilityRule rule) {
        return new ApplicationBusinessException(
                "Unsupported operator for " + rule.getRuleType() + ": " + rule.getOperator()
        );
    }
}
