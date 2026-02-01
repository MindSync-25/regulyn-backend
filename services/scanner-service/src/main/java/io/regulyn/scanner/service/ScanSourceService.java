package io.regulyn.scanner.service;

import com.regulyn.common.audit.AuditWriter;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import io.regulyn.scanner.dto.CreateScanSourceRequest;
import io.regulyn.scanner.dto.ScanSourceResponse;
import io.regulyn.scanner.model.ScanSource;
import io.regulyn.scanner.repository.ScanSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ScanSourceService {

    private final ScanSourceRepository scanSourceRepository;
    private final AuditWriter auditWriter;
    private final OutboxWriter outboxWriter;

    public ScanSourceService(ScanSourceRepository scanSourceRepository,
                              AuditWriter auditWriter,
                              OutboxWriter outboxWriter) {
        this.scanSourceRepository = scanSourceRepository;
        this.auditWriter = auditWriter;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public ScanSourceResponse createSource(CreateScanSourceRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        // Check for duplicate name
        if (scanSourceRepository.existsByTenantIdAndSourceName(tenantId, request.getSourceName())) {
            throw new IllegalArgumentException("Source with name '" + request.getSourceName() + "' already exists");
        }

        ScanSource source = new ScanSource();
        source.setTenantId(tenantId);
        source.setSourceName(request.getSourceName());
        source.setSystemId(request.getSystemId());
        source.setSourceType(request.getSourceType().name());
        source.setStatus(request.getStatus().name());
        source.setBaseUrl(request.getBaseUrl());
        source.setAuthType(request.getAuthType().name());
        source.setAuthRef(request.getAuthRef());
        if (request.getMetadata() != null) {
            source.setMetadata(request.getMetadata());
        }

        source = scanSourceRepository.save(source);

        // Audit
        auditWriter.write(com.regulyn.common.audit.AuditEvent.builder()
                .tenantId(tenantId)
                .action("scanner.source_created")
                .entityType("SCAN_SOURCE")
                .entityId(source.getSourceId().toString())                .payloadHash("N/A")                .payloadHash("N/A")
                .build());

        // Outbox
        EventEnvelopeV1 event = EventFactory.create(
                "scanner.source_created",
                "scanner-service",
                "SCAN_SOURCE",
                source.getSourceId().toString(),
                source
        );
        outboxWriter.write(event);

        return toResponse(source);
    }

    @Transactional(readOnly = true)
    public List<ScanSourceResponse> listSources(String status, String type) {
        UUID tenantId = TenantContextHolder.getTenantId();

        List<ScanSource> sources;
        if (status != null) {
            sources = scanSourceRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
        } else if (type != null) {
            sources = scanSourceRepository.findByTenantIdAndSourceTypeOrderByCreatedAtDesc(tenantId, type);
        } else {
            sources = scanSourceRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        }

        return sources.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public ScanSourceResponse disableSource(UUID sourceId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        ScanSource source = scanSourceRepository.findByTenantIdAndSourceId(tenantId, sourceId)
            .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));

        source.setStatus("DISABLED");
        source = scanSourceRepository.save(source);

        // Audit
        auditWriter.write(com.regulyn.common.audit.AuditEvent.builder()
                .tenantId(tenantId)
                .action("scanner.source_disabled")
                .entityType("SCAN_SOURCE")
                .entityId(source.getSourceId().toString())
                .payloadHash("N/A")
                .build());

        // Outbox
        EventEnvelopeV1 event = EventFactory.create(
                "scanner.source_disabled",
                "scanner-service",
                "SCAN_SOURCE",
                source.getSourceId().toString(),
                source
        );
        outboxWriter.write(event);

        return toResponse(source);
    }

    @Transactional(readOnly = true)
    public ScanSource getSource(UUID sourceId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        return scanSourceRepository.findByTenantIdAndSourceId(tenantId, sourceId)
            .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));
    }

    private ScanSourceResponse toResponse(ScanSource source) {
        ScanSourceResponse response = new ScanSourceResponse();
        response.setSourceId(source.getSourceId());
        response.setSourceName(source.getSourceName());
        response.setSystemId(source.getSystemId());
        response.setSourceType(source.getSourceType());
        response.setStatus(source.getStatus());
        response.setBaseUrl(source.getBaseUrl());
        response.setAuthType(source.getAuthType());
        response.setAuthRef(source.getAuthRef());
        response.setMetadata(source.getMetadata());
        response.setCreatedAt(source.getCreatedAt());
        response.setUpdatedAt(source.getUpdatedAt());
        return response;
    }
}
