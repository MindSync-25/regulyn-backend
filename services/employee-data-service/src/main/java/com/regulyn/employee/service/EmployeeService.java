package com.regulyn.employee.service;

import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.employee.dto.*;
import com.regulyn.employee.model.Employee;
import com.regulyn.employee.model.EmployeeDataRecord;
import com.regulyn.employee.model.Employee.EmployeeStatus;
import com.regulyn.employee.model.HRPurpose;
import com.regulyn.employee.repository.EmployeeDataRecordRepository;
import com.regulyn.employee.repository.EmployeeRepository;
import com.regulyn.employee.repository.HRPurposeRepository;
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
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    private final EmployeeRepository employeeRepository;
    private final HRPurposeRepository hrPurposeRepository;
    private final EmployeeDataRecordRepository employeeDataRecordRepository;
    private final OutboxWriter outboxWriter;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            HRPurposeRepository hrPurposeRepository,
            EmployeeDataRecordRepository employeeDataRecordRepository,
            OutboxWriter outboxWriter) {
        this.employeeRepository = employeeRepository;
        this.hrPurposeRepository = hrPurposeRepository;
        this.employeeDataRecordRepository = employeeDataRecordRepository;
        this.outboxWriter = outboxWriter;
    }

    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        
        try {
            Employee employee = new Employee();
            employee.setTenantId(tenantId);
            employee.setEmployeeRef(request.getEmployeeRef());
            employee.setFullName(request.getFullName());
            employee.setEmail(request.getEmail());
            employee.setDepartment(request.getDepartment());
            employee.setStatus(request.getStatus() != null ? request.getStatus() : Employee.EmployeeStatus.ACTIVE);
            employee.setMetadata(request.getMetadata());
            employee.setCreatedAt(Instant.now());

            employee = employeeRepository.save(employee);

            // Emit employee.created event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("employeeId", employee.getEmployeeId());
            eventPayload.put("tenantId", tenantId);
            eventPayload.put("employeeRef", employee.getEmployeeRef());
            eventPayload.put("email", employee.getEmail());
            eventPayload.put("fullName", employee.getFullName());
            eventPayload.put("department", employee.getDepartment());
            eventPayload.put("status", employee.getStatus().toString());

            EventEnvelopeV1 event = EventFactory.create(
                    "employee.created",
                    "employee-data-service",
                    "employee",
                    employee.getEmployeeId().toString(),
                    eventPayload
            );
            outboxWriter.write(event);

            log.info("Created employee {} for tenant {}", employee.getEmployeeId(), tenantId);
            return toEmployeeResponse(employee);

        } catch (Exception e) {
            log.error("Error creating employee for tenant {}", tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to create employee", e);
        }
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> listEmployees(String q, Employee.EmployeeStatus status) {
        UUID tenantId = TenantContextHolder.getTenantId();
        
        try {
            List<Employee> employees;
            if (q != null && !q.isEmpty() && status != null) {
                employees = employeeRepository.findByTenantIdAndStatusAndFullNameContainingIgnoreCaseOrderByCreatedAtDesc(tenantId, status, q);
            } else if (q != null && !q.isEmpty()) {
                employees = employeeRepository.findByTenantIdAndFullNameContainingIgnoreCaseOrderByCreatedAtDesc(tenantId, q);
            } else if (status != null) {
                employees = employeeRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
            } else {
                employees = employeeRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
            }
            return employees.stream().map(this::toEmployeeResponse).toList();
        } catch (Exception e) {
            log.error("Error listing employees for tenant {}", tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to list employees", e);
        }
    }

    public HRPurposeResponse createHRPurpose(CreateHRPurposeRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        
        try {
            HRPurpose purpose = new HRPurpose();
            purpose.setTenantId(tenantId);
            purpose.setPurposeKey(request.getPurposeKey());
            purpose.setDescription(request.getDescription());
            purpose.setLawfulBasis(request.getLawfulBasis());
            purpose.setRetentionDays(request.getRetentionDays());
            purpose.setSensitive(request.getSensitive() != null ? request.getSensitive() : false);
            purpose.setMetadata(request.getMetadata());
            purpose.setCreatedAt(Instant.now());

            purpose = hrPurposeRepository.save(purpose);

            // Emit hr_purpose.created event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("purposeId", purpose.getHrPurposeId());
            eventPayload.put("tenantId", tenantId);
            eventPayload.put("purposeKey", purpose.getPurposeKey().toString());
            eventPayload.put("lawfulBasis", purpose.getLawfulBasis().toString());
            eventPayload.put("retentionDays", purpose.getRetentionDays());

            EventEnvelopeV1 event = EventFactory.create(
                    "hr_purpose.created",
                    "employee-data-service",
                    "hr_purpose",
                    purpose.getHrPurposeId().toString(),
                    eventPayload
            );
            outboxWriter.write(event);

            log.info("Created HR purpose {} for tenant {}", purpose.getHrPurposeId(), tenantId);
            return toHRPurposeResponse(purpose);

        } catch (Exception e) {
            log.error("Error creating HR purpose for tenant {}", tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to create HR purpose", e);
        }
    }

    @Transactional(readOnly = true)
    public List<HRPurposeResponse> listHRPurposes() {
        UUID tenantId = TenantContextHolder.getTenantId();
        
        try {
            return hrPurposeRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                    .stream().map(this::toHRPurposeResponse).toList();
        } catch (Exception e) {
            log.error("Error listing HR purposes for tenant {}", tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to list HR purposes", e);
        }
    }

    public EmployeeDataRecordResponse createEmployeeDataRecord(CreateEmployeeDataRecordRequest request) {
        UUID tenantId = TenantContextHolder.getTenantId();
        
        try {
            // Validate employee exists
            Employee employee = employeeRepository.findByTenantIdAndEmployeeId(tenantId, request.getEmployeeId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                            "Employee not found"));

            // Validate purpose exists
            HRPurpose purpose = hrPurposeRepository.findByTenantIdAndHrPurposeId(tenantId, request.getHrPurposeId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                            "HR purpose not found"));

            EmployeeDataRecord record = new EmployeeDataRecord();
            record.setTenantId(tenantId);
            record.setEmployeeId(request.getEmployeeId());
            record.setHrPurposeId(request.getHrPurposeId());
            record.setSystemId(request.getSystemId());
            record.setDataCategory(request.getDataCategory());
            record.setNotes(request.getNotes());
            record.setRetentionDaysOverride(request.getRetentionDaysOverride());
            record.setMetadata(request.getMetadata());
            record.setCreatedAt(Instant.now());

            record = employeeDataRecordRepository.save(record);

            // Emit employee_record.created event
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("recordId", record.getRecordId());
            eventPayload.put("tenantId", tenantId);
            eventPayload.put("employeeId", record.getEmployeeId());
            eventPayload.put("hrPurposeId", record.getHrPurposeId());
            eventPayload.put("systemId", record.getSystemId());
            eventPayload.put("dataCategory", record.getDataCategory().toString());

            EventEnvelopeV1 event = EventFactory.create(
                    "employee_record.created",
                    "employee-data-service",
                    "employee_data_record",
                    record.getRecordId().toString(),
                    eventPayload
            );
            outboxWriter.write(event);

            log.info("Created employee data record {} for tenant {}", record.getRecordId(), tenantId);
            return toEmployeeDataRecordResponse(record);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating employee data record for tenant {}", tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to create employee data record", e);
        }
    }

    @Transactional(readOnly = true)
    public List<EmployeeDataRecordResponse> listEmployeeDataRecords(
            UUID employeeId, 
            EmployeeDataRecord.DataCategory dataCategory, 
            UUID hrPurposeId, 
            UUID systemId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        
        try {
            List<EmployeeDataRecord> records;
            
            // Apply filters based on provided parameters
            if (employeeId != null && dataCategory != null) {
                // Filter by both employee and category
                records = employeeDataRecordRepository.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(tenantId, employeeId)
                        .stream()
                        .filter(r -> r.getDataCategory() == dataCategory)
                        .toList();
            } else if (employeeId != null) {
                records = employeeDataRecordRepository.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(tenantId, employeeId);
            } else if (dataCategory != null) {
                records = employeeDataRecordRepository.findByTenantIdAndDataCategoryOrderByCreatedAtDesc(tenantId, dataCategory);
            } else if (hrPurposeId != null) {
                records = employeeDataRecordRepository.findByTenantIdAndHrPurposeIdOrderByCreatedAtDesc(tenantId, hrPurposeId);
            } else if (systemId != null) {
                records = employeeDataRecordRepository.findByTenantIdAndSystemIdOrderByCreatedAtDesc(tenantId, systemId);
            } else {
                records = employeeDataRecordRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
            }
            
            return records.stream().map(this::toEmployeeDataRecordResponse).toList();
        } catch (Exception e) {
            log.error("Error listing employee data records for tenant {}", tenantId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to list employee data records", e);
        }
    }

    // Mapper methods
    private EmployeeResponse toEmployeeResponse(Employee employee) {
        EmployeeResponse response = new EmployeeResponse();
        response.setEmployeeId(employee.getEmployeeId());
        response.setTenantId(employee.getTenantId());
        response.setEmployeeRef(employee.getEmployeeRef());
        response.setFullName(employee.getFullName());
        response.setEmail(employee.getEmail());
        response.setDepartment(employee.getDepartment());
        response.setStatus(employee.getStatus());
        response.setMetadata(employee.getMetadata());
        response.setCreatedAt(employee.getCreatedAt());
        return response;
    }

    private HRPurposeResponse toHRPurposeResponse(HRPurpose purpose) {
        HRPurposeResponse response = new HRPurposeResponse();
        response.setHrPurposeId(purpose.getHrPurposeId());
        response.setTenantId(purpose.getTenantId());
        response.setPurposeKey(purpose.getPurposeKey());
        response.setDescription(purpose.getDescription());
        response.setLawfulBasis(purpose.getLawfulBasis());
        response.setRetentionDays(purpose.getRetentionDays());
        response.setSensitive(purpose.getSensitive());
        response.setMetadata(purpose.getMetadata());
        response.setCreatedAt(purpose.getCreatedAt());
        return response;
    }

    private EmployeeDataRecordResponse toEmployeeDataRecordResponse(EmployeeDataRecord record) {
        EmployeeDataRecordResponse response = new EmployeeDataRecordResponse();
        response.setRecordId(record.getRecordId());
        response.setTenantId(record.getTenantId());
        response.setEmployeeId(record.getEmployeeId());
        response.setDataCategory(record.getDataCategory());
        response.setHrPurposeId(record.getHrPurposeId());
        response.setSystemId(record.getSystemId());
        response.setNotes(record.getNotes());
        response.setRetentionDaysOverride(record.getRetentionDaysOverride());
        response.setMetadata(record.getMetadata());
        response.setCreatedAt(record.getCreatedAt());
        return response;
    }
}
