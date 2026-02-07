package com.regulyn.notification;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class NotificationPersistenceRound2Part1IT {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    private static JdbcTemplate jdbcTemplate;
    
    @BeforeAll
    static void setUp() {
        // Create datasource and run Flyway migrations
        var dataSource = new DriverManagerDataSource(
            postgres.getJdbcUrl(),
            postgres.getUsername(),
            postgres.getPassword()
        );
        
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .schemas("notification")
            .load();
        flyway.migrate();
        
        jdbcTemplate = new JdbcTemplate(dataSource);
    }
    
    @Test
    void tables_exist() {
        // Assert notification.notification_messages exists
        Integer messagesCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'notification' AND table_name = 'notification_messages'",
            Integer.class
        );
        assertThat(messagesCount).isEqualTo(1);
        
        // Assert notification.notification_delivery_receipts exists
        Integer receiptsCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'notification' AND table_name = 'notification_delivery_receipts'",
            Integer.class
        );
        assertThat(receiptsCount).isEqualTo(1);
    }
    
    @Test
    void idempotency_message_hash_unique_per_tenant() throws Exception {
        // Create test request
        UUID requestId = UUID.randomUUID();
        insertNotificationRequest(requestId);
        
        UUID tenant1 = UUID.randomUUID();
        UUID tenant2 = UUID.randomUUID();
        String hash1 = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        
        // Insert message (T1, H1) -> ok
        insertNotificationMessage(tenant1, requestId, hash1);
        
        // Insert message (T1, H1) -> should fail (duplicate)
        assertThatThrownBy(() -> insertNotificationMessage(tenant1, requestId, hash1))
            .hasMessageContaining("duplicate key value violates unique constraint");
        
        // Insert message (T2, H1) -> ok (different tenant)
        insertNotificationMessage(tenant2, requestId, hash1);
    }
    
    @Test
    void idempotency_receipt_payload_hash_unique_per_tenant() throws Exception {
        // Create parent records
        UUID requestId = UUID.randomUUID();
        insertNotificationRequest(requestId);
        UUID messageId = insertNotificationMessage(UUID.randomUUID(), requestId, "aaaa");
        
        UUID tenant1 = UUID.randomUUID();
        UUID tenant2 = UUID.randomUUID();
        String payloadHash1 = "fedcba0987654321fedcba0987654321fedcba0987654321fedcba0987654321";
        
        // Insert receipt (T1, PH1) -> ok
        insertDeliveryReceipt(tenant1, messageId, payloadHash1);
        
        // Insert receipt (T1, PH1) -> should fail (duplicate)
        assertThatThrownBy(() -> insertDeliveryReceipt(tenant1, messageId, payloadHash1))
            .hasMessageContaining("duplicate key value violates unique constraint");
        
        // Insert receipt (T2, PH1) -> ok (different tenant)
        insertDeliveryReceipt(tenant2, messageId, payloadHash1);
    }
    
    @Test
    void audit_outbox_writable_smoke_no_hardcoding() {
        UUID tenantId = UUID.randomUUID();
        
        // Test audit_events insert
        int auditRows = jdbcTemplate.update(
            "INSERT INTO notification.audit_events (tenant_id, service, action, entity_type, occurred_at) " +
            "VALUES (?, ?, ?, ?, NOW())",
            tenantId, "notification-service", "TEST_ACTION", "TestEntity"
        );
        assertThat(auditRows).isEqualTo(1);
        
        // Test outbox_events insert with JSONB cast
        int outboxRows = jdbcTemplate.update(
            "INSERT INTO notification.outbox_events (tenant_id, event_id, event_type, source_service, entity_type, entity_id, correlation_id, payload, payload_hash, occurred_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, NOW())",
            tenantId,
            UUID.randomUUID(),
            "test.event",
            "notification-service",
            "TestEntity",
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "{\"test\": \"data\"}",
            "test-hash"
        );
        assertThat(outboxRows).isEqualTo(1);
        
        // Verify counts increased
        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.audit_events WHERE tenant_id = ?",
            Integer.class,
            tenantId
        );
        assertThat(auditCount).isGreaterThanOrEqualTo(1);
        
        Integer outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notification.outbox_events WHERE tenant_id = ?",
            Integer.class,
            tenantId
        );
        assertThat(outboxCount).isGreaterThanOrEqualTo(1);
    }
    
    // Helper methods
    
    private void insertNotificationRequest(UUID requestId) {
        // First create a template and version to satisfy foreign keys
        UUID templateId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        String tenantId = UUID.randomUUID().toString();
        
        // Insert template
        jdbcTemplate.update(
            "INSERT INTO notification.notification_templates (template_id, tenant_id, template_key, category, default_language, title, created_by, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
            templateId,
            tenantId,
            "TEST_TEMPLATE",
            "LEGAL",
            "en",
            "Test Template",
            "test-user"
        );
        
        // Insert version
        jdbcTemplate.update(
            "INSERT INTO notification.notification_template_versions (version_id, tenant_id, template_id, version_number, created_by, created_at) " +
            "VALUES (?, ?, ?, ?, ?, NOW())",
            versionId,
            tenantId,
            templateId,
            1,
            "test-user"
        );
        
        // Now insert request
        jdbcTemplate.update(
            "INSERT INTO notification.notification_requests (request_id, tenant_id, request_ref, template_id, version_id, language, channel, audience_type, total_recipients, created_by, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
            requestId,
            tenantId,
            "test-ref-" + requestId,
            templateId,
            versionId,
            "en",
            "EMAIL",
            "USER_IDS",
            1,
            "test-user"
        );
    }
    
    private UUID insertNotificationMessage(UUID tenantId, UUID requestId, String messageHashHex) {
        UUID messageId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO notification.notification_messages (id, tenant_id, notification_request_id, recipient, channel, category, message_hash, message_hash_hex, status, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, decode(?, 'hex'), ?, ?, NOW())",
            messageId,
            tenantId,
            requestId,
            "test@example.com",
            "EMAIL",
            "LEGAL",
            messageHashHex.length() < 64 ? String.format("%-64s", messageHashHex).replace(' ', '0') : messageHashHex,
            messageHashHex,
            "QUEUED"
        );
        return messageId;
    }
    
    private void insertDeliveryReceipt(UUID tenantId, UUID messageId, String payloadHashHex) {
        jdbcTemplate.update(
            "INSERT INTO notification.notification_delivery_receipts (id, tenant_id, notification_message_id, provider, status, payload_hash, payload_hash_hex, receipt_payload, created_at) " +
            "VALUES (?, ?, ?, ?, ?, decode(?, 'hex'), ?, ?::jsonb, NOW())",
            UUID.randomUUID(),
            tenantId,
            messageId,
            "TEST_PROVIDER",
            "DELIVERED",
            payloadHashHex.length() < 64 ? String.format("%-64s", payloadHashHex).replace(' ', '0') : payloadHashHex,
            payloadHashHex,
            "{\"status\": \"delivered\"}"
        );
    }
    
}