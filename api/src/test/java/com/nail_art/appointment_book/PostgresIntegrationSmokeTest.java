package com.nail_art.appointment_book;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostgresIntegrationSmokeTest extends PostgresIntegrationTest {
    private static final String SENTINEL_ORG_NAME = "rollback-sentinel";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    void containerAcceptsConnections() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);

        assertThat(result).isEqualTo(1);
    }

    @Test
    @Order(2)
    void flywayAppliesOrganizationsTable() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename = 'organizations'",
                Integer.class
        );

        assertThat(tableCount).isEqualTo(1);
    }

    @Test
    @Order(3)
    @Transactional
    void transactionalTestWritesAreVisibleInsideMethod() {
        jdbcTemplate.update("INSERT INTO organizations (name, timezone) VALUES (?, ?)",
                SENTINEL_ORG_NAME,
                "America/New_York");

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM organizations WHERE name = ?",
                Integer.class,
                SENTINEL_ORG_NAME
        );

        assertThat(rows).isEqualTo(1);
    }

    @Test
    @Order(4)
    void transactionalTestWritesRollBackBetweenMethods() {
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM organizations WHERE name = ?",
                Integer.class,
                SENTINEL_ORG_NAME
        );

        assertThat(rows).isZero();
    }
}
