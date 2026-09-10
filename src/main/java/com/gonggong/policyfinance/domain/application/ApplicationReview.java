package com.gonggong.policyfinance.domain.application;

import com.gonggong.policyfinance.domain.organization.Employee;
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

import java.time.Instant;

@Entity
@Table(name = "application_review")
public class ApplicationReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private PolicyFinanceApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30, updatable = false)
    private ApplicationReviewAction action;

    @Column(name = "comment", nullable = false, length = 1000, updatable = false)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ApplicationReview() {
    }

    public ApplicationReview(
            PolicyFinanceApplication application,
            Employee employee,
            ApplicationReviewAction action,
            String comment,
            Instant createdAt
    ) {
        this.application = application;
        this.employee = employee;
        this.action = action;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public PolicyFinanceApplication getApplication() {
        return application;
    }

    public Employee getEmployee() {
        return employee;
    }

    public ApplicationReviewAction getAction() {
        return action;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
