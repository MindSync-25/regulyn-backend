package com.regulyn.nominee.service;

import com.regulyn.nominee.dto.ExportNomineeRequest;
import com.regulyn.nominee.dto.ExportResponse;
import com.regulyn.nominee.entity.NomineeExport;
import com.regulyn.nominee.repository.NomineeExportRepository;
import com.regulyn.events.publisher.OutboxEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final NomineeExportRepository exportRepository;
    private final OutboxEventPublisher eventPublisher;

    public ExportService(NomineeExportRepository exportRepository,
                         OutboxEventPublisher eventPublisher) {
        this.exportRepository = exportRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ExportResponse requestExport(UUID tenantId, ExportNomineeRequest request, UUID requestedBy) {
        log.info("Requesting export: nominee={}, format={}", request.getNomineeId(), request.getFormat());

        NomineeExport export = new NomineeExport();
        export.setTenantId(tenantId);
        export.setNomineeId(request.getNomineeId());
        export.setFormat(request.getFormat().name());
        export.setStatus("PENDING");
        export.setRequestedAt(Instant.now());
        export.setRequestedBy(requestedBy);
        export.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));

        export = exportRepository.save(export);

        eventPublisher.publish("export.requested", export.getId().toString(), export);

        log.info("Export requested: id={}", export.getId());
        return toResponse(export);
    }

    @Transactional
    public void completeExport(UUID exportId, String downloadUrl, Long fileSizeBytes) {
        log.info("Completing export: id={}", exportId);

        NomineeExport export = exportRepository.findById(exportId)
            .orElseThrow(() -> new IllegalArgumentException("Export not found: " + exportId));

        export.setStatus("COMPLETED");
        export.setDownloadUrl(downloadUrl);
        export.setFileSizeBytes(fileSizeBytes);
        export.setCompletedAt(Instant.now());

        exportRepository.save(export);

        eventPublisher.publish("export.completed", export.getId().toString(), export);

        log.info("Export completed: id={}", exportId);
    }

    public ExportResponse getExport(UUID exportId) {
        NomineeExport export = exportRepository.findById(exportId)
            .orElseThrow(() -> new IllegalArgumentException("Export not found: " + exportId));
        return toResponse(export);
    }

    public List<ExportResponse> getExportsByNominee(UUID nomineeId) {
        return exportRepository.findByNomineeIdOrderByRequestedAtDesc(nomineeId)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private ExportResponse toResponse(NomineeExport export) {
        ExportResponse response = new ExportResponse();
        response.setExportId(export.getId());
        response.setNomineeId(export.getNomineeId());
        response.setFormat(export.getFormat());
        response.setStatus(export.getStatus());
        response.setDownloadUrl(export.getDownloadUrl());
        response.setRequestedAt(export.getRequestedAt());
        response.setRequestedBy(export.getRequestedBy());
        response.setCompletedAt(export.getCompletedAt());
        response.setFileSizeBytes(export.getFileSizeBytes());
        response.setExpiresAt(export.getExpiresAt());
        return response;
    }
}
