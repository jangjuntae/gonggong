package com.gonggong.policyfinance.domain.loan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    boolean existsByApplicationId(Long applicationId);

    Optional<Loan> findByApplicationId(Long applicationId);
}
