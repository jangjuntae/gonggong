package com.gonggong.policyfinance.domain.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "customer",
        uniqueConstraints = @UniqueConstraint(name = "uk_customer_customer_no", columnNames = "customer_no")
)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long id;

    @Column(name = "customer_no", nullable = false, length = 30)
    private String customerNo;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "annual_income", nullable = false, precision = 19, scale = 2)
    private BigDecimal annualIncome;

    @Column(name = "credit_score", nullable = false)
    private Integer creditScore;

    @Column(name = "region", nullable = false, length = 50)
    private String region;

    @Column(name = "business_start_date")
    private LocalDate businessStartDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Customer() {
    }

    public Customer(
            String customerNo,
            String name,
            LocalDate birthDate,
            BigDecimal annualIncome,
            Integer creditScore,
            String region,
            LocalDate businessStartDate
    ) {
        this.customerNo = customerNo;
        this.name = name;
        this.birthDate = birthDate;
        this.annualIncome = annualIncome;
        this.creditScore = creditScore;
        this.region = region;
        this.businessStartDate = businessStartDate;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerNo() {
        return customerNo;
    }

    public String getName() {
        return name;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public BigDecimal getAnnualIncome() {
        return annualIncome;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public String getRegion() {
        return region;
    }

    public LocalDate getBusinessStartDate() {
        return businessStartDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
