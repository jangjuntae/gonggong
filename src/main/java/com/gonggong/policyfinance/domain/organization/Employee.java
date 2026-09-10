package com.gonggong.policyfinance.domain.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "employee",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_employee_no", columnNames = "employee_no"),
                @UniqueConstraint(
                        name = "uk_employee_department_and_id",
                        columnNames = {"department_id", "employee_id"}
                )
        }
)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_id")
    private Long id;

    @Column(name = "employee_no", nullable = false, length = 30)
    private String employeeNo;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Employee() {
    }

    public Employee(String employeeNo, String name, Department department, boolean active) {
        this.employeeNo = employeeNo;
        this.name = name;
        this.department = department;
        this.active = active;
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public String getName() {
        return name;
    }

    public Department getDepartment() {
        return department;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
