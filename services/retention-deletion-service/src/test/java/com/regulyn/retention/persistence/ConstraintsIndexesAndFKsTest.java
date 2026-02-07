package com.regulyn.retention.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstraintsIndexesAndFKsTest extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void constraintsAndIndexesExist() {
        assertConstraintExists("fk_deletion_execution_plan_deletion");
        assertConstraintExists("uq_deletion_execution_plan_version");
        assertConstraintExists("uq_deletion_execution_plan_idempotency");

        assertConstraintExists("fk_deletion_system_execution_deletion");
        assertConstraintExists("fk_deletion_system_execution_plan");
        assertConstraintExists("uq_deletion_system_execution_identity");
        assertConstraintExists("fk_deletion_system_execution_manual_task");

        assertConstraintExists("fk_manual_proof_task_deletion");
        assertConstraintExists("fk_manual_proof_task_plan");
        assertConstraintExists("fk_manual_proof_task_execution");
        assertConstraintExists("uq_manual_proof_task_execution");

        assertConstraintExists("fk_backup_exception_deletion");
        assertConstraintExists("fk_backup_exception_plan");
        assertConstraintExists("fk_backup_exception_execution");

        assertConstraintExists("uq_deletion_tombstone_subject");
        assertConstraintExists("fk_deletion_tombstone_deletion");

        assertIndexExists("idx_deletion_execution_plan_tenant_deletion");
        assertIndexExists("idx_deletion_execution_plan_tenant_status");

        assertIndexExists("idx_deletion_system_execution_tenant_deletion");
        assertIndexExists("idx_deletion_system_execution_tenant_status");
        assertIndexExists("idx_deletion_system_execution_tenant_status_retry");

        assertIndexExists("idx_manual_proof_task_tenant_status");
        assertIndexExists("idx_manual_proof_task_tenant_deletion");

        assertIndexExists("idx_backup_exception_tenant_deletion");
        assertIndexExists("idx_backup_exception_tenant_status_not_before");

        assertIndexExists("idx_deletion_tombstone_tenant_status");
        assertIndexExists("idx_deletion_tombstone_tenant_subject");
    }

    private void assertConstraintExists(String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname = ?",
                Integer.class,
                constraintName
        );
        assertThat(count).isNotNull();
        assertThat(count).isEqualTo(1);
    }

    private void assertIndexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'deletion' AND indexname = ?",
                Integer.class,
                indexName
        );
        assertThat(count).isNotNull();
        assertThat(count).isEqualTo(1);
    }
}
