package com.regulyn.employee.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.employee.dto.CreateEmployeeExportRequest;
import com.regulyn.employee.dto.EmployeeExportResponse;
import com.regulyn.employee.model.Employee;
import com.regulyn.employee.model.EmployeeDataRecord;
import com.regulyn.employee.model.EmployeeExport;
import com.regulyn.employee.model.EmployeeRequest;
import com.regulyn.employee.repository.EmployeeDataRecordRepository;
import com.regulyn.employee.repository.EmployeeExportRepository;
import com.regulyn.employee.repository.EmployeeRepository;
import com.regulyn.employee.repository.EmployeeRequestRepository;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class EmployeeExportService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeExportService.class);

    private final EmployeeExportRepository employeeExportRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeDataRecordRepository employeeDataRecordRepository;
    private final EmployeeRequestRepository employeeRequestRepository;
    private final EvidenceClient evidenceClient;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public EmployeeExportService(
            EmployeeExportRepository employeeExportRepository,
            EmployeeRepository employeeRepository,
            EmployeeDataRecordRepository employeeDataRecordRepository,
            EmployeeRequestRepository employeeRequestRepository,
            EvidenceClient evidenceClient,
            OutboxWriter outboxWriter,
            ObjectMapper objectMapper) {
        this.employeeExportRepository = employeeExportRepository;
        this.employeeRepository = employeeRepository;
        this.employeeDataRecordRepository = employeeDataRecordRepository;
        this.employeeRequestRepository = employeeRequestRepository;
        this.evidenceClient = evidenceClient;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    public EmployeeExportResponse createEmployeeExport(CreateEmployeeExportRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        
        try {
            // Build snapshot of employee compliance data
            Map<String, Object> snapshot = buildSnapshot(tenantId, request.getPeriodFrom(), request.getPeriodTo());
            
            // Create evidence via evidence-reporting-service
            UUID evidenceId = evidenceClient.createEvidence(snapshot);
            
            // Create bundle
            Map<String, Object> bundleData = new HashMap<>();
            bundleData.put("evidenceIds", List.of(evidenceId.toString()));
            bundleData.put("title", request.getTitle() != null ? request.getTitle() : "Employee Compliance Export");
            UUID bundleId = evidenceClient.createBundle(bundleData);
            
            // Create export
            UUID evidenceExportId = evidenceClient.createExport(bundleId);
            
            // Store export record
            EmployeeExport export = new EmployeeExport();
            export.setTenantId(tenantId);
            export.setBundleId(bundleId);
            export.setEvidenceExportId(evidenceExportId);
            export.setTitle(request.getTitle());
            export = employeeExportRepository.save(export);
            
            // Emit event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("exportId", export.getExportId().toString());
            eventPayload.put("bundleId", bundleId.toString());
            eventPayload.put("evidenceExportId", evidenceExportId.toString());
            
            EventEnvelopeV1 event = EventFactory.create(
                    "employee_export.created",
                    "employee-data-service",
                    "employee_export",
                    export.getExportId().toString(),
                    eventPayload
            );
            outboxWriter.write(event);
            
            log.info("Created employee export {} for tenant {}", export.getExportId(), tenantId);
            return toEmployeeExportResponse(export);
            
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating employee export for tenant {}", tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to create employee export", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] downloadExport(UUID exportId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        
        try {
            EmployeeExport export = employeeExportRepository.findByTenantIdAndExportId(tenantId, exportId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                            "Employee export not found"));
            
            // Download from evidence service
            return evidenceClient.downloadExport(export.getEvidenceExportId());
            
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error downloading employee export {} for tenant {}", exportId, tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to download employee export", e);
        }
    }

    private Map<String, Object> buildSnapshot(UUID tenantId, Instant periodFrom, Instant periodTo) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("snapshotType", "employee_compliance");
        snapshot.put("tenantId", tenantId.toString());
        snapshot.put("generatedAt", Instant.now().toString());
        
        if (periodFrom != null) {
            snapshot.put("periodFrom", periodFrom.toString());
        }
        if (periodTo != null) {
            snapshot.put("periodTo", periodTo.toString());
        }
        
        // Get all employees for tenant
        List<Employee> employees = employeeRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        snapshot.put("employees", employees.stream().map(this::toMap).toList());
        
        // Get all data records for the period (if specified)
        List<EmployeeDataRecord> records;
        if (periodFrom != null && periodTo != null) {
            records = employeeDataRecordRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                    .stream()
                    .filter(r -> !r.getCreatedAt().isBefore(periodFrom) && !r.getCreatedAt().isAfter(periodTo))
                    .toList();
        } else {
            records = employeeDataRecordRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        }
        snapshot.put("dataRecords", records.stream().map(this::toMap).toList());
        
        // Get all requests for the period (if specified)
        List<EmployeeRequest> requests;
        if (periodFrom != null && periodTo != null) {
            requests = employeeRequestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, org.springframework.data.domain.Pageable.unpaged())
                    .getContent()
                    .stream()
                    .filter(r -> !r.getCreatedAt().isBefore(periodFrom) && !r.getCreatedAt().isAfter(periodTo))
                    .toList();
        } else {
            requests = employeeRequestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        }
        snapshot.put("requests", requests.stream().map(this::toMap).toList());
        
        return snapshot;
    }

    private Map<String, Object> toMap(Object obj) {
        return objectMapper.convertValue(obj, Map.class);
    }

    private EmployeeExportResponse toEmployeeExportResponse(EmployeeExport export) {
        EmployeeExportResponse response = new EmployeeExportResponse();
        response.setExportId(export.getExportId());
        response.setBundleId(export.getBundleId());
        response.setEvidenceExportId(export.getEvidenceExportId());
        response.setDownloadPath("/api/employee/exports/" + export.getExportId() + "/download");
        response.setCreatedAt(export.getCreatedAt());
        return response;
    }
}
