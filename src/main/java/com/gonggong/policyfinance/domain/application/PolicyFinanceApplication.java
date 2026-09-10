package com.gonggong.policyfinance.domain.application;

import com.gonggong.policyfinance.domain.customer.Customer;
import com.gonggong.policyfinance.domain.product.FinancialProduct;
import com.gonggong.policyfinance.domain.product.ProductRuleVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "application")
public class PolicyFinanceApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private FinancialProduct financialProduct;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_version_id", nullable = false, updatable = false)
    private ProductRuleVersion productRuleVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ApplicationStatus status;

    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "received_at")
    private Instant submittedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PolicyFinanceApplication() {
    }

    public PolicyFinanceApplication(
            Customer customer,
            FinancialProduct financialProduct,
            ProductRuleVersion productRuleVersion,
            BigDecimal requestedAmount
    ) {
        validateProductRuleVersion(financialProduct, productRuleVersion);
        this.customer = customer;
        this.financialProduct = financialProduct;
        this.productRuleVersion = productRuleVersion;
        this.requestedAmount = requestedAmount;
        this.status = ApplicationStatus.DRAFT;
    }

    private void validateProductRuleVersion(
            FinancialProduct financialProduct,
            ProductRuleVersion productRuleVersion
    ) {
        if (financialProduct == null || productRuleVersion == null) {
            throw new ApplicationBusinessException("Financial product and product rule version are required");
        }

        FinancialProduct versionProduct = productRuleVersion.getFinancialProduct();
        boolean sameInstance = financialProduct == versionProduct;
        boolean samePersistedId = financialProduct.getId() != null
                && versionProduct != null
                && financialProduct.getId().equals(versionProduct.getId());

        if (!sameInstance && !samePersistedId) {
            throw new ApplicationBusinessException(
                    "Product rule version does not belong to financial product"
            );
        }
    }

    public void submit(Instant submittedAt) {
        transitionTo(ApplicationStatus.RECEIVED);
        this.submittedAt = submittedAt;
    }

    public void startEligibilityCheck() {
        transitionTo(ApplicationStatus.ELIGIBILITY_CHECK);
    }

    public void startReview() {
        transitionTo(ApplicationStatus.UNDER_REVIEW);
    }

    public void requestSupplement() {
        transitionTo(ApplicationStatus.SUPPLEMENT_REQUESTED);
    }

    public void resumeReview() {
        transitionTo(ApplicationStatus.UNDER_REVIEW);
    }

    public void approve() {
        transitionTo(ApplicationStatus.APPROVED);
    }

    public void reject() {
        transitionTo(ApplicationStatus.REJECTED);
    }

    public void execute() {
        transitionTo(ApplicationStatus.EXECUTED);
    }

    private void transitionTo(ApplicationStatus nextStatus) {
        status.validateTransition(nextStatus);
        this.status = nextStatus;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public FinancialProduct getFinancialProduct() {
        return financialProduct;
    }

    public ProductRuleVersion getProductRuleVersion() {
        return productRuleVersion;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
