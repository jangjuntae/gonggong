package com.gonggong.policyfinance.domain.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    @Query("select history from StatusHistory history "
            + "where history.application.id = :applicationId "
            + "order by history.changedAt, history.id")
    List<StatusHistory> findByApplicationIdOrderByChangedAt(Long applicationId);
}
