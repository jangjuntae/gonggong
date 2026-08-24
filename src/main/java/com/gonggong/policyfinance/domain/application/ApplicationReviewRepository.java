package com.gonggong.policyfinance.domain.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ApplicationReviewRepository extends JpaRepository<ApplicationReview, Long> {

    @Query("select review from ApplicationReview review "
            + "where review.application.id = :applicationId "
            + "order by review.createdAt, review.id")
    List<ApplicationReview> findByApplicationIdOrderByCreatedAt(Long applicationId);
}
