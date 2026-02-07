package com.regulyn.retention.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class NewTablesExistTest extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void newTablesExist() {
        assertThat(tableExists("deletion_execution_plan")).isTrue();
        assertThat(tableExists("deletion_system_execution")).isTrue();
        assertThat(tableExists("deletion_manual_proof_task")).isTrue();
        assertThat(tableExists("deletion_backup_exception")).isTrue();
        assertThat(tableExists("deletion_tombstone")).isTrue();
    }

    private boolean tableExists(String tableName) {
        String regclass = jdbcTemplate.queryForObject(
                "SELECT to_regclass('deletion.' || ?)",
                String.class,
                tableName
        );
        return regclass != null;
    }
}
