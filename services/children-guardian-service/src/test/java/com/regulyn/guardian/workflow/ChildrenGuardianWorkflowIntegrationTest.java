package com.regulyn.guardian.workflow;

import com.regulyn.guardian.config.TestConfig;
import com.regulyn.guardian.entity.Child;
import com.regulyn.guardian.entity.Guardian;
import com.regulyn.guardian.entity.GuardianConsent;
import com.regulyn.guardian.repository.ChildRepository;
import com.regulyn.guardian.repository.GuardianConsentRepository;
import com.regulyn.guardian.repository.GuardianRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(TestConfig.class)
public class ChildrenGuardianWorkflowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("guardian")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ChildRepository childRepository;

    @Autowired
    private GuardianRepository guardianRepository;

    @Autowired
    private GuardianConsentRepository guardianConsentRepository;

    @Test
    public void test01_createChild_minor_isChild() {
        UUID tenantId = UUID.randomUUID();
        
        // Create child
        Child child = new Child();
        child.setTenantId(tenantId);
        child.setChildRef("TEST-CHILD-001");
        child.setFullName("John Doe");
        child.setDateOfBirth(LocalDate.now().minusYears(10)); // 10 years old
        child.setCountry("US");
        child.setStatus("ACTIVE");

        Child savedChild = childRepository.save(child);

        assertThat(savedChild.getChildId()).isNotNull();
        assertThat(savedChild.getChildRef()).isEqualTo("TEST-CHILD-001");
        assertThat(savedChild.getCreatedAt()).isNotNull();
    }

    @Test
    public void test02_createGuardian_pendingVerification() {
        UUID tenantId = UUID.randomUUID();
        
        // Create child first
        Child child = new Child();
        child.setTenantId(tenantId);
        child.setChildRef("TEST-CHILD-002");
        child.setFullName("Jane Smith");
        child.setDateOfBirth(LocalDate.now().minusYears(12));
        child.setCountry("US");
        child.setStatus("ACTIVE");
        Child savedChild = childRepository.save(child);

        // Create guardian
        Guardian guardian = new Guardian();
        guardian.setTenantId(tenantId);
        guardian.setChildId(savedChild.getChildId());
        guardian.setGuardianName("Parent Smith");
        guardian.setGuardianEmail("parent@example.com");
        guardian.setGuardianPhone("+1234567890");
        guardian.setRelationship("PARENT");
        guardian.setStatus("PENDING");
        guardian.setVerificationRequired(true);
        guardian.setCreatedAt(Instant.now());
        guardian.setUpdatedAt(Instant.now());

        Guardian savedGuardian = guardianRepository.save(guardian);

        assertThat(savedGuardian.getGuardianId()).isNotNull();
        assertThat(savedGuardian.getStatus()).isEqualTo("PENDING");
        assertThat(savedGuardian.isVerificationRequired()).isTrue();
    }

    @Test
    public void test03_createGuardian_verifiedStatus() {
        UUID tenantId = UUID.randomUUID();
        
        // Create child
        Child child = new Child();
        child.setTenantId(tenantId);
        child.setChildRef("TEST-CHILD-003");
        child.setFullName("Bob Jones");
        child.setDateOfBirth(LocalDate.now().minusYears(8));
        child.setCountry("UK");
        child.setStatus("ACTIVE");
        Child savedChild = childRepository.save(child);

        // Create guardian with verified status
        Guardian guardian = new Guardian();
        guardian.setTenantId(tenantId);
        guardian.setChildId(savedChild.getChildId());
        guardian.setGuardianName("Guardian Jones");
        guardian.setGuardianEmail("guardian@example.com");
        guardian.setGuardianPhone("+9876543210");
        guardian.setRelationship("PARENT");
        guardian.setStatus("VERIFIED");
        guardian.setVerificationRequired(true);
        guardian.setVerifiedAt(Instant.now());
        guardian.setVerifiedBy(UUID.randomUUID());
        guardian.setCreatedAt(Instant.now());
        guardian.setUpdatedAt(Instant.now());

        Guardian savedGuardian = guardianRepository.save(guardian);

        assertThat(savedGuardian.getGuardianId()).isNotNull();
        assertThat(savedGuardian.getStatus()).isEqualTo("VERIFIED");
        assertThat(savedGuardian.getVerifiedAt()).isNotNull();
        assertThat(savedGuardian.getVerifiedBy()).isNotNull();
    }

    @Test
    public void test04_createConsent_submitted() {
        UUID tenantId = UUID.randomUUID();
        
        // Create child
        Child child = new Child();
        child.setTenantId(tenantId);
        child.setChildRef("TEST-CHILD-004");
        child.setFullName("Alice Brown");
        child.setDateOfBirth(LocalDate.now().minusYears(14));
        child.setCountry("CA");
        child.setStatus("ACTIVE");
        Child savedChild = childRepository.save(child);

        // Create verified guardian
        Guardian guardian = new Guardian();
        guardian.setTenantId(tenantId);
        guardian.setChildId(savedChild.getChildId());
        guardian.setGuardianName("Parent Brown");
        guardian.setGuardianEmail("parent.brown@example.com");
        guardian.setRelationship("PARENT");
        guardian.setStatus("VERIFIED");
        guardian.setVerificationRequired(true);
        guardian.setVerifiedAt(Instant.now());
        guardian.setVerifiedBy(UUID.randomUUID());
        guardian.setCreatedAt(Instant.now());
        guardian.setUpdatedAt(Instant.now());
        Guardian savedGuardian = guardianRepository.save(guardian);

        // Create consent
        GuardianConsent consent = new GuardianConsent();
        consent.setTenantId(tenantId);
        consent.setChildId(savedChild.getChildId());
        consent.setGuardianId(savedGuardian.getGuardianId());
        consent.setPurposeKey("DATA_PROCESSING");
        consent.setConsentScope("FULL");
        consent.setStatus("SUBMITTED");
        consent.setRequiresApproval(true);
        consent.setIdempotencyKey(UUID.randomUUID().toString());
        consent.setValidFrom(LocalDate.now());
        consent.setValidTo(LocalDate.now().plusYears(1));
        consent.setCreatedAt(Instant.now());
        consent.setUpdatedAt(Instant.now());

        GuardianConsent savedConsent = guardianConsentRepository.save(consent);

        assertThat(savedConsent.getConsentId()).isNotNull();
        assertThat(savedConsent.getStatus()).isEqualTo("SUBMITTED");
        assertThat(savedConsent.getPurposeKey()).isEqualTo("DATA_PROCESSING");
        assertThat(savedConsent.isRequiresApproval()).isTrue();
    }

    @Test
    public void test05_createConsent_approved() {
        UUID tenantId = UUID.randomUUID();
        
        // Create child
        Child child = new Child();
        child.setTenantId(tenantId);
        child.setChildRef("TEST-CHILD-005");
        child.setFullName("Charlie Davis");
        child.setDateOfBirth(LocalDate.now().minusYears(15));
        child.setCountry("AU");
        child.setStatus("ACTIVE");
        Child savedChild = childRepository.save(child);

        // Create verified guardian
        Guardian guardian = new Guardian();
        guardian.setTenantId(tenantId);
        guardian.setChildId(savedChild.getChildId());
        guardian.setGuardianName("Parent Davis");
        guardian.setGuardianEmail("parent.davis@example.com");
        guardian.setRelationship("PARENT");
        guardian.setStatus("VERIFIED");
        guardian.setVerifiedAt(Instant.now());
        guardian.setVerifiedBy(UUID.randomUUID());
        guardian.setCreatedAt(Instant.now());
        guardian.setUpdatedAt(Instant.now());
        Guardian savedGuardian = guardianRepository.save(guardian);

        // Create approved consent
        GuardianConsent consent = new GuardianConsent();
        consent.setTenantId(tenantId);
        consent.setChildId(savedChild.getChildId());
        consent.setGuardianId(savedGuardian.getGuardianId());
        consent.setPurposeKey("MARKETING");
        consent.setConsentScope("LIMITED");
        consent.setStatus("APPROVED");
        consent.setRequiresApproval(true);
        consent.setIdempotencyKey(UUID.randomUUID().toString());
        consent.setValidFrom(LocalDate.now());
        consent.setValidTo(LocalDate.now().plusYears(2));
        consent.setApprovedBy(UUID.randomUUID());
        consent.setApprovedAt(Instant.now());
        consent.setCreatedAt(Instant.now());
        consent.setUpdatedAt(Instant.now());

        GuardianConsent savedConsent = guardianConsentRepository.save(consent);

        assertThat(savedConsent.getConsentId()).isNotNull();
        assertThat(savedConsent.getStatus()).isEqualTo("APPROVED");
        assertThat(savedConsent.getApprovedBy()).isNotNull();
        assertThat(savedConsent.getApprovedAt()).isNotNull();
    }

    @Test
    public void test06_findChildByRef() {
        UUID tenantId = UUID.randomUUID();
        String childRef = "TEST-CHILD-006";
        
        // Create child
        Child child = new Child();
        child.setTenantId(tenantId);
        child.setChildRef(childRef);
        child.setFullName("Emma Wilson");
        child.setDateOfBirth(LocalDate.now().minusYears(11));
        child.setCountry("NZ");
        child.setStatus("ACTIVE");
        childRepository.save(child);

        // Find by childRef
        Child found = childRepository.findByTenantIdAndChildRef(tenantId, childRef).orElse(null);

        assertThat(found).isNotNull();
        assertThat(found.getChildRef()).isEqualTo(childRef);
        assertThat(found.getFullName()).isEqualTo("Emma Wilson");
    }

    @Test
    public void test07_findGuardiansByChildId() {
        UUID tenantId = UUID.randomUUID();
        
        // Create child
        Child child = new Child();
        child.setTenantId(tenantId);
        child.setChildRef("TEST-CHILD-007");
        child.setFullName("Frank Miller");
        child.setDateOfBirth(LocalDate.now().minusYears(9));
        child.setStatus("ACTIVE");
        Child savedChild = childRepository.save(child);

        // Create two guardians for the same child
        Guardian guardian1 = new Guardian();
        guardian1.setTenantId(tenantId);
        guardian1.setChildId(savedChild.getChildId());
        guardian1.setGuardianName("Mother Miller");
        guardian1.setGuardianEmail("mother@example.com");
        guardian1.setRelationship("PARENT");
        guardian1.setStatus("VERIFIED");
        guardian1.setCreatedAt(Instant.now());
        guardian1.setUpdatedAt(Instant.now());
        guardianRepository.save(guardian1);

        Guardian guardian2 = new Guardian();
        guardian2.setTenantId(tenantId);
        guardian2.setChildId(savedChild.getChildId());
        guardian2.setGuardianName("Father Miller");
        guardian2.setGuardianEmail("father@example.com");
        guardian2.setRelationship("PARENT");
        guardian2.setStatus("PENDING");
        guardian2.setCreatedAt(Instant.now());
        guardian2.setUpdatedAt(Instant.now());
        guardianRepository.save(guardian2);

        // Find guardians by child ID
        var guardians = guardianRepository.findByTenantIdAndChildId(tenantId, savedChild.getChildId());

        assertThat(guardians).hasSize(2);
    }

    @Test
    public void test08_findConsentsByGuardianId() {
        UUID tenantId = UUID.randomUUID();
        
        // Create child
        Child child = new Child();
        child.setTenantId(tenantId);
        child.setChildRef("TEST-CHILD-008");
        child.setFullName("Grace Taylor");
        child.setDateOfBirth(LocalDate.now().minusYears(13));
        child.setStatus("ACTIVE");
        Child savedChild = childRepository.save(child);

        // Create guardian
        Guardian guardian = new Guardian();
        guardian.setTenantId(tenantId);
        guardian.setChildId(savedChild.getChildId());
        guardian.setGuardianName("Parent Taylor");
        guardian.setGuardianEmail("parent.taylor@example.com");
        guardian.setRelationship("PARENT");
        guardian.setStatus("VERIFIED");
        guardian.setCreatedAt(Instant.now());
        guardian.setUpdatedAt(Instant.now());
        Guardian savedGuardian = guardianRepository.save(guardian);

        // Create multiple consents
        for (int i = 0; i < 3; i++) {
            GuardianConsent consent = new GuardianConsent();
            consent.setTenantId(tenantId);
            consent.setChildId(savedChild.getChildId());
            consent.setGuardianId(savedGuardian.getGuardianId());
            consent.setPurposeKey("PURPOSE_" + i);
            consent.setConsentScope("FULL");
            consent.setStatus("SUBMITTED");
            consent.setIdempotencyKey(UUID.randomUUID().toString());
            consent.setCreatedAt(Instant.now());
            consent.setUpdatedAt(Instant.now());
            guardianConsentRepository.save(consent);
        }

        // Find consents by guardian ID
        var consents = guardianConsentRepository.findByTenantIdAndGuardianId(tenantId, savedGuardian.getGuardianId());

        assertThat(consents).hasSize(3);
    }

    @Test
    public void test09_idempotencyKey_uniqueness() {
        UUID tenantId = UUID.randomUUID();
        String idempotencyKey = "UNIQUE-KEY-" + UUID.randomUUID();
        
        // Create child and guardian
        Child child = new Child();
        child.setTenantId(tenantId);
        child.setChildRef("TEST-CHILD-009");
        child.setFullName("Henry Anderson");
        child.setDateOfBirth(LocalDate.now().minusYears(16));
        child.setStatus("ACTIVE");
        Child savedChild = childRepository.save(child);

        Guardian guardian = new Guardian();
        guardian.setTenantId(tenantId);
        guardian.setChildId(savedChild.getChildId());
        guardian.setGuardianName("Parent Anderson");
        guardian.setGuardianEmail("anderson@example.com");
        guardian.setRelationship("PARENT");
        guardian.setStatus("VERIFIED");
        guardian.setCreatedAt(Instant.now());
        guardian.setUpdatedAt(Instant.now());
        Guardian savedGuardian = guardianRepository.save(guardian);

        // Create consent with idempotency key
        GuardianConsent consent = new GuardianConsent();
        consent.setTenantId(tenantId);
        consent.setChildId(savedChild.getChildId());
        consent.setGuardianId(savedGuardian.getGuardianId());
        consent.setPurposeKey("DATA_SHARING");
        consent.setConsentScope("FULL");
        consent.setStatus("SUBMITTED");
        consent.setIdempotencyKey(idempotencyKey);
        consent.setCreatedAt(Instant.now());
        consent.setUpdatedAt(Instant.now());

        GuardianConsent savedConsent = guardianConsentRepository.save(consent);

        // Find by idempotency key
        GuardianConsent found = guardianConsentRepository.findByIdempotencyKey(
            tenantId,
            savedChild.getChildId(),
            savedGuardian.getGuardianId(),
            "DATA_SHARING",
            idempotencyKey
        ).orElse(null);

        assertThat(found).isNotNull();
        assertThat(found.getConsentId()).isEqualTo(savedConsent.getConsentId());
    }

    @Test
    public void test10_multipleChildrenForTenant() {
        UUID tenantId = UUID.randomUUID();
        
        // Create multiple children for same tenant
        for (int i = 0; i < 5; i++) {
            Child child = new Child();
            child.setTenantId(tenantId);
            child.setChildRef("MULTI-CHILD-" + i);
            child.setFullName("Child " + i);
            child.setDateOfBirth(LocalDate.now().minusYears(10 + i));
            child.setStatus("ACTIVE");
            childRepository.save(child);
        }

        // Find all children for tenant
        var children = childRepository.findAll();
        var tenantChildren = children.stream()
            .filter(c -> c.getTenantId().equals(tenantId))
            .toList();

        assertThat(tenantChildren).hasSize(5);
    }
}
