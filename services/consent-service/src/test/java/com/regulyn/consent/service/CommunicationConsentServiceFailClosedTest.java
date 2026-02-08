package com.regulyn.consent.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.common.audit.AuditWriter;
import com.regulyn.consent.client.EvidenceClient;
import com.regulyn.consent.entity.CommunicationChannel;
import com.regulyn.consent.model.CommunicationConsentBatchResponse;
import com.regulyn.consent.model.CommunicationConsentStatusResponse;
import com.regulyn.consent.repository.CommunicationConsentLedgerRepository;
import com.regulyn.consent.repository.NoticeLanguageTextRepository;
import com.regulyn.events.outbox.OutboxWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunicationConsentServiceFailClosedTest {

    @Mock
    private CommunicationConsentLedgerRepository ledgerRepository;

    @Mock
    private NoticeLanguageTextRepository noticeLanguageTextRepository;

    @Mock
    private AuditWriter auditWriter;

    @Mock
    private OutboxWriter outboxWriter;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Mock
    private EvidenceClient evidenceClient;

    private CommunicationConsentService service;

    @BeforeEach
    void setUp() {
        service = new CommunicationConsentService(
                ledgerRepository,
                noticeLanguageTextRepository,
                auditWriter,
                outboxWriter,
                objectMapper,
                evidenceClient,
                "consent-service"
        );

        TenantContext context = new TenantContext();
        context.setTenantId(UUID.randomUUID());
        context.setUserId(UUID.randomUUID());
        context.setRequestId("test-request");
        context.setTraceId("test-trace");
        TenantContextHolder.setContext(context);
    }

    @Test
    void getStatusFailsClosedOnError() {
        UUID principalId = UUID.randomUUID();
        when(ledgerRepository.findFirstByTenantIdAndDataPrincipalIdAndChannelOrderByEffectiveAtDesc(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(principalId),
                org.mockito.ArgumentMatchers.eq(CommunicationChannel.EMAIL))
        ).thenThrow(new RuntimeException("boom"));

        CommunicationConsentStatusResponse response = service.getStatus(CommunicationChannel.EMAIL, principalId);

        assertThat(response.state()).isEqualTo("UNKNOWN");
        assertThat(response.ledgerId()).isNull();
    }

    @Test
    void batchStatusFailsClosedOnError() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        when(ledgerRepository.findLatestByTenantAndChannelAndPrincipalIds(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList())
        ).thenThrow(new RuntimeException("boom"));

        CommunicationConsentBatchResponse response = service.batchStatus(
                CommunicationChannel.SMS,
                List.of(a, b)
        );

        assertThat(response.failClosed()).isTrue();
        assertThat(response.results()).hasSize(2);
        assertThat(response.results().stream().allMatch(r -> "UNKNOWN".equals(r.state()))).isTrue();
        assertThat(response.results().stream().allMatch(r -> !r.allowed())).isTrue();
    }
}
