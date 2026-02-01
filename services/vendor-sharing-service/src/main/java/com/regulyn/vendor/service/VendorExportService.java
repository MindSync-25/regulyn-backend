package com.regulyn.vendor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.vendor.client.EvidenceClient;
import com.regulyn.vendor.dto.CreateVendorExportRequest;
import com.regulyn.vendor.dto.VendorExportResponse;
import com.regulyn.vendor.model.SharingRecord;
import com.regulyn.vendor.model.Vendor;
import com.regulyn.vendor.model.VendorExport;
import com.regulyn.vendor.repository.SharingRecordRepository;
import com.regulyn.vendor.repository.VendorExportRepository;
import com.regulyn.vendor.repository.VendorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VendorExportService {

    private static final Logger log = LoggerFactory.getLogger(VendorExportService.class);

    private final VendorRepository vendorRepository;
    private final SharingRecordRepository sharingRecordRepository;
    private final VendorExportRepository vendorExportRepository;
    private final EvidenceClient evidenceClient;
    private final ObjectMapper objectMapper;
    private final OutboxWriter outboxWriter;

    public VendorExportService(
        VendorRepository vendorRepository,
        SharingRecordRepository sharingRecordRepository,
        VendorExportRepository vendorExportRepository,
        EvidenceClient evidenceClient,
        ObjectMapper objectMapper,
        OutboxWriter outboxWriter
    ) {
        this.vendorRepository = vendorRepository;
        this.sharingRecordRepository = sharingRecordRepository;
        this.vendorExportRepository = vendorExportRepository;
        this.evidenceClient = evidenceClient;
        this.objectMapper = objectMapper;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public VendorExportResponse createVendorExport(CreateVendorExportRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Instant now = Instant.now();

        // Determine date range
        Instant fromDate = request.getFromDate() != null ? request.getFromDate() : Instant.EPOCH;
        Instant toDate = request.getToDate() != null ? request.getToDate() : now;

        // Build snapshot
        Map<String, Object> snapshot = buildSnapshot(tenantId, fromDate, toDate, request.getVendorId(), request.getDataCategory());
        
        try {
            String snapshotJson = objectMapper.writeValueAsString(snapshot);
            String snapshotHash = computeSha256(snapshotJson);

            // Step 1: Create evidence record
            Map<String, Object> evidenceData = new HashMap<>();
            evidenceData.put("type", "VENDOR_SHARING_SNAPSHOT");
            evidenceData.put("generatedAt", now.toString());
            evidenceData.put("filters", buildFilters(fromDate, toDate, request.getVendorId(), request.getDataCategory()));
            evidenceData.put("snapshotHash", snapshotHash);
            evidenceData.put("snapshotJson", snapshot);

            UUID evidenceId = evidenceClient.createEvidence(evidenceData);

            // Step 2: Create bundle
            Map<String, Object> bundleData = new HashMap<>();
            bundleData.put("bundleType", "AUDIT_EXPORT");
            bundleData.put("referenceType", "PERIOD");
            bundleData.put("referenceId", "vendor-sharing:" + fromDate + ":" + toDate);
            bundleData.put("evidenceIds", List.of(evidenceId.toString()));

            UUID bundleId = evidenceClient.createBundle(bundleData);

            // Step 3: Create export
            UUID evidenceExportId = evidenceClient.createExport(bundleId);

            // Step 4: Save vendor export record
            VendorExport export = new VendorExport();
            export.setTenantId(tenantId);
            export.setBundleId(bundleId);
            export.setEvidenceExportId(evidenceExportId);
            export = vendorExportRepository.save(export);

            VendorExportResponse response = new VendorExportResponse();
            response.setExportId(export.getExportId());
            response.setBundleId(bundleId);
            response.setEvidenceExportId(evidenceExportId);
            response.setCreatedAt(export.getCreatedAt());
            response.setDownloadUrl("/vendor/exports/" + export.getExportId() + "/download");

            // Emit event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("exportId", export.getExportId().toString());
            eventPayload.put("bundleId", bundleId.toString());
            eventPayload.put("evidenceExportId", evidenceExportId.toString());
            writeOutboxEvent(tenantId, "sharing.export_created", eventPayload);

            return response;

        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, 
                "Evidence service is unavailable: " + e.getMessage());
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Failed to serialize snapshot: " + e.getMessage());
        }
    }

    public byte[] downloadVendorExport(UUID exportId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        VendorExport export = vendorExportRepository.findByTenantIdAndExportId(tenantId, exportId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Export not found"));

        try {
            return evidenceClient.downloadExport(export.getEvidenceExportId());
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, 
                "Evidence service is unavailable: " + e.getMessage());
        }
    }

    private Map<String, Object> buildSnapshot(UUID tenantId, Instant from, Instant to, UUID vendorId, String dataCategory) {
        Map<String, Object> snapshot = new HashMap<>();

        // Get vendors
        List<Vendor> vendors;
        if (vendorId != null) {
            vendors = vendorRepository.findByTenantIdAndVendorId(tenantId, vendorId)
                .map(List::of)
                .orElse(List.of());
        } else {
            vendors = vendorRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        }

        // Get sharing records
        List<SharingRecord> sharingRecords = sharingRecordRepository.findByTenantIdAndDateRange(tenantId, from, to);

        // Filter by vendorId if specified
        if (vendorId != null) {
            sharingRecords = sharingRecords.stream()
                .filter(r -> r.getVendorId().equals(vendorId))
                .collect(Collectors.toList());
        }

        // Filter by dataCategory if specified
        if (dataCategory != null && !dataCategory.isBlank()) {
            sharingRecords = sharingRecords.stream()
                .filter(r -> r.getDataCategories().contains(dataCategory))
                .collect(Collectors.toList());
        }

        snapshot.put("vendors", vendors.stream()
            .map(this::vendorToMap)
            .collect(Collectors.toList()));
        snapshot.put("sharingRecords", sharingRecords.stream()
            .map(this::sharingRecordToMap)
            .collect(Collectors.toList()));
        snapshot.put("exportedAt", Instant.now().toString());
        snapshot.put("totalVendors", vendors.size());
        snapshot.put("totalSharingRecords", sharingRecords.size());

        return snapshot;
    }

    private Map<String, Object> buildFilters(Instant from, Instant to, UUID vendorId, String dataCategory) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("fromDate", from.toString());
        filters.put("toDate", to.toString());
        if (vendorId != null) {
            filters.put("vendorId", vendorId.toString());
        }
        if (dataCategory != null) {
            filters.put("dataCategory", dataCategory);
        }
        return filters;
    }

    private Map<String, Object> vendorToMap(Vendor vendor) {
        Map<String, Object> map = new HashMap<>();
        map.put("vendorId", vendor.getVendorId().toString());
        map.put("vendorName", vendor.getVendorName());
        map.put("vendorType", vendor.getVendorType().name());
        map.put("country", vendor.getCountry());
        map.put("hostingRegion", vendor.getHostingRegion().name());
        map.put("enabled", vendor.getEnabled());
        map.put("riskLevel", vendor.getRiskLevel().name());
        map.put("createdAt", vendor.getCreatedAt().toString());
        return map;
    }

    private Map<String, Object> sharingRecordToMap(SharingRecord record) {
        Map<String, Object> map = new HashMap<>();
        map.put("sharingId", record.getSharingId().toString());
        map.put("vendorId", record.getVendorId().toString());
        if (record.getActivityId() != null) {
            map.put("activityId", record.getActivityId().toString());
        }
        if (record.getSystemId() != null) {
            map.put("systemId", record.getSystemId().toString());
        }
        map.put("sharingPurpose", record.getSharingPurpose());
        map.put("lawfulBasis", record.getLawfulBasis().name());
        map.put("dataCategories", record.getDataCategories());
        map.put("frequency", record.getFrequency().name());
        map.put("transferCrossBorder", record.getTransferCrossBorder());
        if (record.getTransferToRegions() != null) {
            map.put("transferToRegions", record.getTransferToRegions());
        }
        map.put("enabled", record.getEnabled());
        map.put("status", record.getStatus().name());
        map.put("createdAt", record.getCreatedAt().toString());
        return map;
    }

    private String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private void writeOutboxEvent(UUID tenantId, String eventType, Map<String, Object> payload) {
        try {
            String entityId = payload.containsKey("exportId") ? payload.get("exportId").toString() : "";
            EventEnvelopeV1 event = EventFactory.create(
                eventType,
                "vendor-sharing-service",
                "vendor_export",
                entityId,
                payload
            );
            outboxWriter.write(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", e.getMessage());
        }
    }
}
