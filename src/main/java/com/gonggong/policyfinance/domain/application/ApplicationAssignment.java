package com.gonggong.policyfinance.domain.application;

import com.gonggong.policyfinance.domain.organization.Department;
import com.gonggong.policyfinance.domain.organization.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "application_assignment")
public class ApplicationAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private PolicyFinanceApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false, updatable = false, insertable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "department_id", referencedColumnName = "department_id", nullable = false),
            @JoinColumn(name = "employee_id", referencedColumnName = "employee_id", nullable = false)
    })
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by_employee_id", nullable = false, updatable = false)
    private Employee assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    protected ApplicationAssignment() {
    }

    public ApplicationAssignment(
            PolicyFinanceApplication application,
            Department department,
            Employee employee,
            Employee assignedBy,
            Instant assignedAt,
            String reason
    ) {
        validateDepartment(employee, department);
        this.application = application;
        this.department = department;
        this.employee = employee;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
        this.active = true;
        this.reason = reason;
    }

    public void release(Instant releasedAt) {
        if (!active) {
            throw new ApplicationBusinessException("Application assignment is already inactive");
        }
        this.active = false;
        this.releasedAt = releasedAt;
    }

    private void validateDepartment(Employee employee, Department department) {
        if (employee == null || department == null || employee.getDepartment() == null) {
            throw new ApplicationBusinessException("Employee and department are required for assignment");
        }
        boolean sameInstance = employee.getDepartment() == department;
        boolean samePersistedId = employee.getDepartment().getId() != null
                && employee.getDepartment().getId().equals(department.getId());
        if (!sameInstance && !samePersistedId) {
            throw new ApplicationBusinessException("Employee does not belong to assignment department");
        }
    }

    public Long getId() {
        return id;
    }

    public PolicyFinanceApplication getApplication() {
        return application;
    }

    public Department getDepartment() {
        return department;
    }

    public Employee getEmployee() {
        return employee;
    }

    public Employee getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public boolean isActive() {
        return active;
    }

    public String getReason() {
        return reason;
    }
}
