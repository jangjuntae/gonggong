package com.gonggong.policyfinance.domain.application;

import com.gonggong.policyfinance.domain.organization.Department;
import com.gonggong.policyfinance.domain.organization.DepartmentRepository;
import com.gonggong.policyfinance.domain.organization.Employee;
import com.gonggong.policyfinance.domain.organization.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class ApplicationAssignmentService {

    private final PolicyFinanceApplicationRepository applicationRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final ApplicationAssignmentRepository assignmentRepository;
    private final Clock clock;

    public ApplicationAssignmentService(
            PolicyFinanceApplicationRepository applicationRepository,
            DepartmentRepository departmentRepository,
            EmployeeRepository employeeRepository,
            ApplicationAssignmentRepository assignmentRepository,
            Clock clock
    ) {
        this.applicationRepository = applicationRepository;
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.assignmentRepository = assignmentRepository;
        this.clock = clock;
    }

    @Transactional
    public ApplicationAssignment assign(
            Long applicationId,
            Long departmentId,
            Long employeeId,
            Long assignedByEmployeeId,
            String reason
    ) {
        validateReason(reason);
        PolicyFinanceApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationBusinessException("Application not found: " + applicationId));
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ApplicationBusinessException("Department not found: " + departmentId));
        Employee employee = findActiveEmployee(employeeId, "Assigned employee");
        Employee assignedBy = findActiveEmployee(assignedByEmployeeId, "Assigning employee");

        if (!department.isActive()) {
            throw new ApplicationBusinessException("Department is not active: " + departmentId);
        }
        if (!employee.getDepartment().getId().equals(departmentId)) {
            throw new ApplicationBusinessException("Employee does not belong to assignment department");
        }

        Instant assignedAt = clock.instant();
        assignmentRepository.findByApplicationIdAndActiveTrue(applicationId).ifPresent(current -> {
            current.release(assignedAt);
            assignmentRepository.flush();
        });

        return assignmentRepository.saveAndFlush(new ApplicationAssignment(
                application, department, employee, assignedBy, assignedAt, reason
        ));
    }

    private Employee findActiveEmployee(Long employeeId, String label) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ApplicationBusinessException(label + " not found: " + employeeId));
        if (!employee.isActive()) {
            throw new ApplicationBusinessException(label + " is not active: " + employeeId);
        }
        return employee;
    }

    private void validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApplicationBusinessException("Assignment reason is required");
        }
    }
}
