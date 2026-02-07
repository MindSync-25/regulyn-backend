package com.regulyn.retention.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FlywayMigrationAppliesV7Test extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliedV7Migration() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '7'",
                Integer.class
        );

        assertThat(count).isNotNull();
        assertThat(count).isGreaterThan(0);

        List<String> versions = jdbcTemplate.queryForList(
            "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",
            String.class
        );
        assertThat(versions).containsExactly("1", "2", "3", "5", "6", "7");

        Integer duplicateCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM (" +
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL GROUP BY version HAVING COUNT(*) > 1" +
                ") dup",
            Integer.class
        );
        assertThat(duplicateCount).isNotNull();
        assertThat(duplicateCount).isZero();
    }
}
