package com.gonggong.policyfinance.domain.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ApplicationAssignmentRepository extends JpaRepository<ApplicationAssignment, Long> {

    Optional<ApplicationAssignment> findByApplicationIdAndActiveTrue(Long applicationId);

    @Query("select assignment from ApplicationAssignment assignment "
            + "where assignment.application.id = :applicationId "
            + "order by assignment.assignedAt, assignment.id")
    List<ApplicationAssignment> findByApplicationIdOrderByAssignedAt(Long applicationId);
}
