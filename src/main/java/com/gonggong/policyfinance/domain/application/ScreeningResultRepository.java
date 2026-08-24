package com.gonggong.policyfinance.domain.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreeningResultRepository extends JpaRepository<ScreeningResult, Long> {

    List<ScreeningResult> findByApplicationIdOrderById(Long applicationId);
}
