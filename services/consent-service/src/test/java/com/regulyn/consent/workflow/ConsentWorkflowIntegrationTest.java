package com.regulyn.consent.workflow;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.consent.ConsentServiceApplication;
import com.regulyn.consent.entity.*;
import com.regulyn.consent.model.*;
import com.regulyn.consent.repository.*;
import com.regulyn.consent.service.NoticeManagementService;
import com.regulyn.events.outbox.OutboxEvent;
import com.regulyn.events.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = ConsentServiceApplication.class)
@Testcontainers
public class ConsentWorkflowIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("consent")
        .withUsername("test")
        .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    private NoticeManagementService noticeManagementService;
    
    @Autowired
    private NoticeTemplateRepository noticeTemplateRepository;
    
    @Autowired
    private NoticeVersionRepository noticeVersionRepository;
    
    @Autowired
    private NoticeLanguageTextRepository noticeLanguageTextRepository;
    
    @Autowired
    private ConsentReceiptRepository consentReceiptRepository;
    
    @Autowired
    private ConsentStatusHistoryRepository consentStatusHistoryRepository;
    
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    
    private UUID testTenantId;
    private UUID testUserId;
    
    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        
        // Set tenant context
        TenantContext context = new TenantContext();
        context.setTenantId(testTenantId);
        context.setUserId(testUserId);
        context.setRequestId("test-request");
        context.setTraceId("test-trace");
        TenantContextHolder.setContext(context);
    }
    
    @Test
    void testNoticePublishFlow() {
        // 1. Create notice
        CreateNoticeRequest createNoticeReq = new CreateNoticeRequest(
            "marketing",
            "Marketing Communications",
            "promotional",
            "en"
        );
        CreateNoticeResponse noticeResp = noticeManagementService.createNotice(createNoticeReq);
        assertThat(noticeResp.noticeId()).isNotNull();
        
        // Verify notice created event
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).anyMatch(e -> e.getEventType().equals("notice.created"));
        
        // 2. Create version
        CreateVersionRequest versionReq = new CreateVersionRequest("Initial version");
        CreateVersionResponse versionResp = noticeManagementService.createVersion(
            noticeResp.noticeId(), versionReq);
        assertThat(versionResp.versionId()).isNotNull();
        assertThat(versionResp.versionNumber()).isEqualTo(1);
        
        // 3. Add language
        AddLanguageRequest languageReq = new AddLanguageRequest("en", 
            "We would like to send you marketing emails about our products and services.");
        AddLanguageResponse languageResp = noticeManagementService.addLanguage(
            noticeResp.noticeId(), versionResp.versionId(), languageReq);
        assertThat(languageResp.languageId()).isNotNull();
        assertThat(languageResp.contentHash()).isNotNull();
        
        // 4. Publish version
        PublishVersionResponse publishResp = noticeManagementService.publishVersion(
            noticeResp.noticeId(), versionResp.versionId());
        assertThat(publishResp.published()).isTrue();
        assertThat(publishResp.publishedAt()).isNotNull();
        
        // Verify exactly one published version exists
        List<NoticeVersion> publishedVersions = noticeVersionRepository
            .findByTenantIdAndNoticeIdAndStatusOrderByVersionNumberDesc(
                testTenantId, noticeResp.noticeId(), "PUBLISHED");
        assertThat(publishedVersions).hasSize(1);
        
        // Verify published event
        events = outboxEventRepository.findAll(); // Reload events after publish
        assertThat(events).anyMatch(e -> e.getEventType().equals("notice.published"));
        
        // 5. Create and publish second version (should retire first)
        CreateVersionResponse version2Resp = noticeManagementService.createVersion(
            noticeResp.noticeId(), new CreateVersionRequest("Updated version"));
        noticeManagementService.addLanguage(
            noticeResp.noticeId(), version2Resp.versionId(), 
            new AddLanguageRequest("en", "Updated marketing communications text."));
        noticeManagementService.publishVersion(noticeResp.noticeId(), version2Resp.versionId());
        
        // Verify first version is retired
        NoticeVersion firstVersion = noticeVersionRepository.findById(versionResp.versionId()).get();
        assertThat(firstVersion.getStatus()).isEqualTo("RETIRED");
        
        // Verify only one published version exists
        publishedVersions = noticeVersionRepository
            .findByTenantIdAndNoticeIdAndStatusOrderByVersionNumberDesc(
                testTenantId, noticeResp.noticeId(), "PUBLISHED");
        assertThat(publishedVersions).hasSize(1);
        assertThat(publishedVersions.get(0).getVersionNumber()).isEqualTo(2);
    }
    
    @Test
    void testConsentGrantFlow() {
        // Setup: Create and publish a notice
        CreateNoticeResponse noticeResp = noticeManagementService.createNotice(
            new CreateNoticeRequest("analytics", "Analytics Tracking", "analytics", "en"));
        CreateVersionResponse versionResp = noticeManagementService.createVersion(
            noticeResp.noticeId(), new CreateVersionRequest("Initial"));
        noticeManagementService.addLanguage(
            noticeResp.noticeId(), versionResp.versionId(),
            new AddLanguageRequest("en", "We track your usage for analytics purposes."));
        noticeManagementService.publishVersion(noticeResp.noticeId(), versionResp.versionId());
        
        // Grant consent
        UUID dataPrincipalId = UUID.randomUUID();
        GrantConsentRequest grantReq = new GrantConsentRequest(
            dataPrincipalId,
            "analytics",
            "en",
            "WIDGET",
            "client-ref-123",
            "idempotency-key-001"
        );
        
        GrantConsentResponse grantResp = noticeManagementService.grantConsent(grantReq);
        
        assertThat(grantResp.receiptId()).isNotNull();
        assertThat(grantResp.status()).isEqualTo("GRANTED");
        assertThat(grantResp.receiptHash()).isNotNull();
        assertThat(grantResp.noticeVersionId()).isEqualTo(versionResp.versionId());
        assertThat(grantResp.contentHash()).isNotNull();
        
        // Verify receipt created
        ConsentReceiptEntity receipt = consentReceiptRepository.findById(grantResp.receiptId()).get();
        assertThat(receipt.getStatus()).isEqualTo("GRANTED");
        assertThat(receipt.getDataPrincipalId()).isEqualTo(dataPrincipalId);
        
        // Verify outbox event
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).anyMatch(e -> e.getEventType().equals("consent.granted"));
        
        // Test idempotency - same idempotencyKey should return same receipt
        GrantConsentResponse grantResp2 = noticeManagementService.grantConsent(grantReq);
        assertThat(grantResp2.receiptId()).isEqualTo(grantResp.receiptId());
    }
    
    @Test
    void testConsentWithdrawFlow() {
        // Setup: Create, publish notice, and grant consent
        CreateNoticeResponse noticeResp = noticeManagementService.createNotice(
            new CreateNoticeRequest("newsletter", "Newsletter", "promotional", "en"));
        CreateVersionResponse versionResp = noticeManagementService.createVersion(
            noticeResp.noticeId(), new CreateVersionRequest("Initial"));
        noticeManagementService.addLanguage(
            noticeResp.noticeId(), versionResp.versionId(),
            new AddLanguageRequest("en", "Subscribe to our newsletter."));
        noticeManagementService.publishVersion(noticeResp.noticeId(), versionResp.versionId());
        
        UUID dataPrincipalId = UUID.randomUUID();
        GrantConsentRequest grantReq = new GrantConsentRequest(
            dataPrincipalId, "newsletter", "en", "PORTAL", null, null);
        GrantConsentResponse grantResp = noticeManagementService.grantConsent(grantReq);
        
        // Withdraw consent
        WithdrawConsentRequest withdrawReq = new WithdrawConsentRequest("User requested opt-out");
        WithdrawConsentResponse withdrawResp = noticeManagementService.withdrawConsent(
            grantResp.receiptId(), withdrawReq);
        
        assertThat(withdrawResp.receiptId()).isEqualTo(grantResp.receiptId());
        assertThat(withdrawResp.status()).isEqualTo("WITHDRAWN");
        assertThat(withdrawResp.withdrawnAt()).isNotNull();
        
        // Verify status updated
        ConsentReceiptEntity receipt = consentReceiptRepository.findById(grantResp.receiptId()).get();
        assertThat(receipt.getStatus()).isEqualTo("WITHDRAWN");
        assertThat(receipt.getWithdrawnAt()).isNotNull();
        
        // Verify history record created
        List<ConsentStatusHistory> history = consentStatusHistoryRepository.findAll();
        assertThat(history).anyMatch(h -> 
            h.getReceiptId().equals(grantResp.receiptId()) &&
            h.getFromStatus().equals("GRANTED") &&
            h.getToStatus().equals("WITHDRAWN")
        );
        
        // Verify outbox event
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).anyMatch(e -> e.getEventType().equals("consent.withdrawn"));
        
        // Cannot withdraw again
        assertThrows(IllegalStateException.class, () -> 
            noticeManagementService.withdrawConsent(grantResp.receiptId(), withdrawReq));
    }
    
    @Test
    void testListConsents() {
        // Setup
        CreateNoticeResponse noticeResp = noticeManagementService.createNotice(
            new CreateNoticeRequest("terms", "Terms of Service", "legal", "en"));
        CreateVersionResponse versionResp = noticeManagementService.createVersion(
            noticeResp.noticeId(), new CreateVersionRequest("Initial"));
        noticeManagementService.addLanguage(
            noticeResp.noticeId(), versionResp.versionId(),
            new AddLanguageRequest("en", "Terms and conditions apply."));
        noticeManagementService.publishVersion(noticeResp.noticeId(), versionResp.versionId());
        
        // Grant multiple consents
        UUID dataPrincipalId = UUID.randomUUID();
        GrantConsentRequest grantReq1 = new GrantConsentRequest(
            dataPrincipalId, "terms", "en", "WIDGET", null, "key-1");
        noticeManagementService.grantConsent(grantReq1);
        
        // Create another notice
        CreateNoticeResponse notice2Resp = noticeManagementService.createNotice(
            new CreateNoticeRequest("privacy", "Privacy Policy", "legal", "en"));
        CreateVersionResponse version2Resp = noticeManagementService.createVersion(
            notice2Resp.noticeId(), new CreateVersionRequest("Initial"));
        noticeManagementService.addLanguage(
            notice2Resp.noticeId(), version2Resp.versionId(),
            new AddLanguageRequest("en", "Privacy policy text."));
        noticeManagementService.publishVersion(notice2Resp.noticeId(), version2Resp.versionId());
        
        GrantConsentRequest grantReq2 = new GrantConsentRequest(
            dataPrincipalId, "privacy", "en", "PORTAL", null, "key-2");
        noticeManagementService.grantConsent(grantReq2);
        
        // List all consents for data principal
        List<ConsentReceiptDto> receipts = noticeManagementService.listConsents(dataPrincipalId, null);
        assertThat(receipts).hasSize(2);
        
        // List by purpose
        List<ConsentReceiptDto> termsReceipts = noticeManagementService.listConsents(dataPrincipalId, "terms");
        assertThat(termsReceipts).hasSize(1);
        assertThat(termsReceipts.get(0).purpose()).isEqualTo("terms");
    }
}
