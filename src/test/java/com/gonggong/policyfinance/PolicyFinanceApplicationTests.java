package com.gonggong.policyfinance;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PolicyFinanceApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private EntityManager entityManager;

    @Test
    void contextLoadsWithPostgreSqlFlywayAndJpa() {
        Number metadataCount = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM app_metadata WHERE metadata_key = 'schema_version'"
        ).getSingleResult();
        Number flywayMigrationCount = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true"
        ).getSingleResult();

        assertThat(metadataCount.longValue()).isEqualTo(1L);
        assertThat(flywayMigrationCount.longValue()).isGreaterThanOrEqualTo(1L);
    }
}
