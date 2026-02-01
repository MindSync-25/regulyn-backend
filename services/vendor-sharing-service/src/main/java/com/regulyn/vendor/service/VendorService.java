package com.regulyn.vendor.service;

import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import com.regulyn.vendor.dto.*;
import com.regulyn.vendor.model.SharingRecord;
import com.regulyn.vendor.model.SharingStatusHistory;
import com.regulyn.vendor.model.Vendor;
import com.regulyn.vendor.model.VendorAgreement;
import com.regulyn.vendor.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VendorService {

    private static final Logger log = LoggerFactory.getLogger(VendorService.class);

    private final VendorRepository vendorRepository;
    private final VendorAgreementRepository agreementRepository;
    private final SharingRecordRepository sharingRecordRepository;
    private final SharingStatusHistoryRepository statusHistoryRepository;
    private final OutboxWriter outboxWriter;

    public VendorService(
        VendorRepository vendorRepository,
        VendorAgreementRepository agreementRepository,
        SharingRecordRepository sharingRecordRepository,
        SharingStatusHistoryRepository statusHistoryRepository,
        OutboxWriter outboxWriter
    ) {
        this.vendorRepository = vendorRepository;
        this.agreementRepository = agreementRepository;
        this.sharingRecordRepository = sharingRecordRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public VendorResponse createVendor(CreateVendorRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        Vendor vendor = new Vendor();
        vendor.setTenantId(tenantId);
        vendor.setVendorName(request.getVendorName());
        vendor.setVendorType(request.getVendorType());
        vendor.setContactEmail(request.getContactEmail());
        vendor.setCountry(request.getCountry());
        vendor.setHostingRegion(request.getHostingRegion());
        vendor.setRiskLevel(request.getRiskLevel());
        vendor.setEnabled(true);
        vendor.setMetadata(request.getMetadata() != null ? request.getMetadata() : new HashMap<>());

        vendor = vendorRepository.save(vendor);

        // Emit event
        Map<String, Object> payload = new HashMap<>();
        payload.put("vendorId", vendor.getVendorId().toString());
        payload.put("vendorName", vendor.getVendorName());
        payload.put("vendorType", vendor.getVendorType().name());
        payload.put("riskLevel", vendor.getRiskLevel().name());
        writeOutboxEvent(tenantId, "vendor.created", payload);

        return toVendorResponse(vendor);
    }

    public List<VendorResponse> listVendors(Boolean enabled, Vendor.RiskLevel riskLevel, String q) {
        UUID tenantId = TenantContextHolder.getTenantId();

        List<Vendor> vendors;
        if (q != null && !q.isBlank()) {
            if (enabled != null) {
                vendors = vendorRepository.searchByNameAndEnabled(tenantId, enabled, q);
            } else {
                vendors = vendorRepository.searchByName(tenantId, q);
            }
        } else if (enabled != null && riskLevel != null) {
            vendors = vendorRepository.findByTenantIdAndEnabledAndRiskLevelOrderByCreatedAtDesc(tenantId, enabled, riskLevel);
        } else if (enabled != null) {
            vendors = vendorRepository.findByTenantIdAndEnabledOrderByCreatedAtDesc(tenantId, enabled);
        } else if (riskLevel != null) {
            vendors = vendorRepository.findByTenantIdAndRiskLevelOrderByCreatedAtDesc(tenantId, riskLevel);
        } else {
            vendors = vendorRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        }

        return vendors.stream()
            .map(this::toVendorResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public VendorResponse disableVendor(UUID vendorId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        Vendor vendor = vendorRepository.findByTenantIdAndVendorId(tenantId, vendorId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));

        vendor.setEnabled(false);
        vendor = vendorRepository.save(vendor);

        // Emit event
        Map<String, Object> payload = new HashMap<>();
        payload.put("vendorId", vendor.getVendorId().toString());
        payload.put("vendorName", vendor.getVendorName());
        writeOutboxEvent(tenantId, "vendor.disabled", payload);

        return toVendorResponse(vendor);
    }

    @Transactional
    public AgreementResponse createAgreement(UUID vendorId, CreateAgreementRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        Vendor vendor = vendorRepository.findByTenantIdAndVendorId(tenantId, vendorId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));

        VendorAgreement agreement = new VendorAgreement();
        agreement.setTenantId(tenantId);
        agreement.setVendorId(vendor.getVendorId());
        agreement.setAgreementType(request.getAgreementType());
        agreement.setStatus(request.getStatus());
        agreement.setEffectiveFrom(request.getEffectiveFrom());
        agreement.setEffectiveTo(request.getEffectiveTo());
        agreement.setDocRef(request.getDocRef());
        agreement.setNotes(request.getNotes());

        agreement = agreementRepository.save(agreement);

        // Emit event
        Map<String, Object> payload = new HashMap<>();
        payload.put("agreementId", agreement.getAgreementId().toString());
        payload.put("vendorId", agreement.getVendorId().toString());
        payload.put("agreementType", agreement.getAgreementType().name());
        payload.put("status", agreement.getStatus().name());
        writeOutboxEvent(tenantId, "vendor.agreement_added", payload);

        return toAgreementResponse(agreement);
    }

    public List<AgreementResponse> listAgreements(UUID vendorId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        vendorRepository.findByTenantIdAndVendorId(tenantId, vendorId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));

        List<VendorAgreement> agreements = agreementRepository.findByTenantIdAndVendorIdOrderByCreatedAtDesc(tenantId, vendorId);
        return agreements.stream()
            .map(this::toAgreementResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public SharingRecordResponse createSharingRecord(CreateSharingRecordRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();

        Vendor vendor = vendorRepository.findByTenantIdAndVendorId(tenantId, request.getVendorId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));

        if (!vendor.getEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot create sharing record for disabled vendor");
        }

        if (Boolean.TRUE.equals(request.getTransferCrossBorder())) {
            if (request.getTransferToRegions() == null || request.getTransferToRegions().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "transferToRegions is required when transferCrossBorder is true");
            }
        }

        if (request.getStartAt() != null && request.getEndAt() != null) {
            if (request.getEndAt().isBefore(request.getStartAt())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endAt must be after startAt");
            }
        }

        SharingRecord record = new SharingRecord();
        record.setTenantId(tenantId);
        record.setVendorId(request.getVendorId());
        record.setActivityId(request.getActivityId());
        record.setSystemId(request.getSystemId());
        record.setSharingPurpose(request.getSharingPurpose());
        record.setLawfulBasis(request.getLawfulBasis());
        record.setDataCategories(request.getDataCategories());
        record.setFrequency(request.getFrequency());
        record.setTransferCrossBorder(request.getTransferCrossBorder() != null ? request.getTransferCrossBorder() : false);
        record.setTransferToRegions(request.getTransferToRegions());
        record.setTransferNotes(request.getTransferNotes());
        record.setStartAt(request.getStartAt());
        record.setEndAt(request.getEndAt());
        record.setEnabled(true);
        record.setMetadata(request.getMetadata() != null ? request.getMetadata() : new HashMap<>());
        record.setStatus(SharingRecord.SharingStatus.ACTIVE);

        record = sharingRecordRepository.save(record);

        // Emit event
        Map<String, Object> payload = new HashMap<>();
        payload.put("sharingId", record.getSharingId().toString());
        payload.put("vendorId", record.getVendorId().toString());
        payload.put("sharingPurpose", record.getSharingPurpose());
        payload.put("transferCrossBorder", record.getTransferCrossBorder());
        writeOutboxEvent(tenantId, "sharing.created", payload);

        return toSharingRecordResponse(record);
    }

    @Transactional
    public DisableSharingResponse disableSharingRecord(UUID sharingId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        SharingRecord record = sharingRecordRepository.findByTenantIdAndSharingId(tenantId, sharingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sharing record not found"));

        String oldStatus = record.getStatus().name();
        record.setEnabled(false);
        record.setStatus(SharingRecord.SharingStatus.INACTIVE);
        sharingRecordRepository.save(record);

        SharingStatusHistory history = new SharingStatusHistory();
        history.setTenantId(tenantId);
        history.setSharingId(sharingId);
        history.setFromStatus(oldStatus);
        history.setToStatus(SharingRecord.SharingStatus.INACTIVE.name());
        history.setReason("Disabled by user");
        statusHistoryRepository.save(history);

        // Emit event
        Map<String, Object> payload = new HashMap<>();
        payload.put("sharingId", sharingId.toString());
        payload.put("fromStatus", oldStatus);
        payload.put("toStatus", SharingRecord.SharingStatus.INACTIVE.name());
        writeOutboxEvent(tenantId, "sharing.disabled", payload);

        DisableSharingResponse response = new DisableSharingResponse();
        response.setSharingId(sharingId);
        response.setEnabled(false);
        response.setStatus(SharingRecord.SharingStatus.INACTIVE);
        response.setMessage("Sharing record disabled successfully");
        return response;
    }

    public Page<SharingRecordResponse> listSharingRecords(UUID vendorId, UUID activityId, UUID systemId, Boolean enabled,
                                                          String dataCategory, Boolean transferCrossBorder, Pageable pageable) {
        UUID tenantId = TenantContextHolder.getTenantId();

        Page<SharingRecord> records;
        if (vendorId != null) {
            records = sharingRecordRepository.findByTenantIdAndVendorIdOrderByCreatedAtDesc(tenantId, vendorId, pageable);
        } else if (activityId != null) {
            records = sharingRecordRepository.findByTenantIdAndActivityIdOrderByCreatedAtDesc(tenantId, activityId, pageable);
        } else if (systemId != null) {
            records = sharingRecordRepository.findByTenantIdAndSystemIdOrderByCreatedAtDesc(tenantId, systemId, pageable);
        } else if (enabled != null) {
            records = sharingRecordRepository.findByTenantIdAndEnabledOrderByCreatedAtDesc(tenantId, enabled, pageable);
        } else if (dataCategory != null) {
            records = sharingRecordRepository.findByDataCategory(tenantId, dataCategory, pageable);
        } else if (transferCrossBorder != null) {
            records = sharingRecordRepository.findByTenantIdAndTransferCrossBorderOrderByCreatedAtDesc(tenantId, transferCrossBorder, pageable);
        } else {
            records = sharingRecordRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        }

        return records.map(this::toSharingRecordResponse);
    }

    public SharingRecordDetailResponse getSharingRecord(UUID sharingId) {
        UUID tenantId = TenantContextHolder.getTenantId();

        SharingRecord record = sharingRecordRepository.findByTenantIdAndSharingId(tenantId, sharingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sharing record not found"));

        Vendor vendor = vendorRepository.findByTenantIdAndVendorId(tenantId, record.getVendorId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));

        SharingRecordDetailResponse response = new SharingRecordDetailResponse();
        response.setSharingId(record.getSharingId());
        response.setVendorId(record.getVendorId());
        response.setVendorName(vendor.getVendorName());
        response.setActivityId(record.getActivityId());
        response.setSystemId(record.getSystemId());
        response.setSharingPurpose(record.getSharingPurpose());
        response.setLawfulBasis(record.getLawfulBasis());
        response.setDataCategories(record.getDataCategories());
        response.setFrequency(record.getFrequency());
        response.setTransferCrossBorder(record.getTransferCrossBorder());
        response.setTransferToRegions(record.getTransferToRegions());
        response.setTransferNotes(record.getTransferNotes());
        response.setStartAt(record.getStartAt());
        response.setEndAt(record.getEndAt());
        response.setEnabled(record.getEnabled());
        response.setMetadata(record.getMetadata());
        response.setStatus(record.getStatus());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }

    private VendorResponse toVendorResponse(Vendor vendor) {
        VendorResponse response = new VendorResponse();
        response.setVendorId(vendor.getVendorId());
        response.setVendorName(vendor.getVendorName());
        response.setVendorType(vendor.getVendorType());
        response.setContactEmail(vendor.getContactEmail());
        response.setCountry(vendor.getCountry());
        response.setHostingRegion(vendor.getHostingRegion());
        response.setEnabled(vendor.getEnabled());
        response.setRiskLevel(vendor.getRiskLevel());
        response.setMetadata(vendor.getMetadata());
        response.setCreatedAt(vendor.getCreatedAt());
        response.setUpdatedAt(vendor.getUpdatedAt());
        return response;
    }

    private AgreementResponse toAgreementResponse(VendorAgreement agreement) {
        AgreementResponse response = new AgreementResponse();
        response.setAgreementId(agreement.getAgreementId());
        response.setVendorId(agreement.getVendorId());
        response.setAgreementType(agreement.getAgreementType());
        response.setStatus(agreement.getStatus());
        response.setEffectiveFrom(agreement.getEffectiveFrom());
        response.setEffectiveTo(agreement.getEffectiveTo());
        response.setDocRef(agreement.getDocRef());
        response.setNotes(agreement.getNotes());
        response.setCreatedAt(agreement.getCreatedAt());
        return response;
    }

    private SharingRecordResponse toSharingRecordResponse(SharingRecord record) {
        SharingRecordResponse response = new SharingRecordResponse();
        response.setSharingId(record.getSharingId());
        response.setVendorId(record.getVendorId());
        response.setActivityId(record.getActivityId());
        response.setSystemId(record.getSystemId());
        response.setSharingPurpose(record.getSharingPurpose());
        response.setLawfulBasis(record.getLawfulBasis());
        response.setDataCategories(record.getDataCategories());
        response.setFrequency(record.getFrequency());
        response.setTransferCrossBorder(record.getTransferCrossBorder());
        response.setTransferToRegions(record.getTransferToRegions());
        response.setTransferNotes(record.getTransferNotes());
        response.setStartAt(record.getStartAt());
        response.setEndAt(record.getEndAt());
        response.setEnabled(record.getEnabled());
        response.setMetadata(record.getMetadata());
        response.setStatus(record.getStatus());
        response.setCreatedAt(record.getCreatedAt());
        response.setUpdatedAt(record.getUpdatedAt());
        return response;
    }

    private void writeOutboxEvent(UUID tenantId, String eventType, Map<String, Object> payload) {
        try {
            String entityId = payload.containsKey("vendorId") ? payload.get("vendorId").toString() :
                             (payload.containsKey("sharingId") ? payload.get("sharingId").toString() : "");
            EventEnvelopeV1 event = EventFactory.create(
                eventType,
                "vendor-sharing-service",
                "vendor",
                entityId,
                payload
            );
            outboxWriter.write(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", e.getMessage());
        }
    }
}
