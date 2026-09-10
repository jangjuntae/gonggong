package com.gonggong.policyfinance.domain.application;

import com.gonggong.policyfinance.domain.customer.Customer;
import com.gonggong.policyfinance.domain.customer.CustomerRepository;
import com.gonggong.policyfinance.domain.product.FinancialProduct;
import com.gonggong.policyfinance.domain.product.FinancialProductRepository;
import com.gonggong.policyfinance.domain.product.ProductRuleVersion;
import com.gonggong.policyfinance.domain.product.ProductRuleVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

@Service
public class ApplicationService {

    private final CustomerRepository customerRepository;
    private final FinancialProductRepository financialProductRepository;
    private final ProductRuleVersionRepository productRuleVersionRepository;
    private final PolicyFinanceApplicationRepository applicationRepository;
    private final Clock clock;

    public ApplicationService(
            CustomerRepository customerRepository,
            FinancialProductRepository financialProductRepository,
            ProductRuleVersionRepository productRuleVersionRepository,
            PolicyFinanceApplicationRepository applicationRepository,
            Clock clock
    ) {
        this.customerRepository = customerRepository;
        this.financialProductRepository = financialProductRepository;
        this.productRuleVersionRepository = productRuleVersionRepository;
        this.applicationRepository = applicationRepository;
        this.clock = clock;
    }

    @Transactional
    public PolicyFinanceApplication createApplication(Long customerId, Long productId, BigDecimal requestedAmount) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ApplicationBusinessException("Customer not found: " + customerId));
        FinancialProduct product = financialProductRepository.findById(productId)
                .orElseThrow(() -> new ApplicationBusinessException("Financial product not found: " + productId));

        if (!product.isActive()) {
            throw new ApplicationBusinessException("Financial product is not active: " + productId);
        }

        validateRequestedAmount(requestedAmount, product.getMaxApplicationAmount());

        Instant applicationTime = clock.instant();
        ProductRuleVersion ruleVersion = productRuleVersionRepository.findEffectiveVersion(productId, applicationTime)
                .orElseThrow(() -> new ApplicationBusinessException(
                        "Active product rule version not found: " + productId
                ));

        return applicationRepository.save(new PolicyFinanceApplication(
                customer,
                product,
                ruleVersion,
                requestedAmount
        ));
    }

    @Transactional
    public PolicyFinanceApplication submitApplication(Long applicationId) {
        PolicyFinanceApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationBusinessException("Application not found: " + applicationId));
        application.submit(clock.instant());
        return application;
    }

    private void validateRequestedAmount(BigDecimal requestedAmount, BigDecimal maximumAmount) {
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApplicationBusinessException("Requested amount must be greater than zero");
        }
        if (requestedAmount.compareTo(maximumAmount) > 0) {
            throw new ApplicationBusinessException("Requested amount exceeds product maximum amount");
        }
    }
}
