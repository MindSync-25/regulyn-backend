package com.regulyn.employee.controller;

import com.regulyn.employee.dto.*;
import com.regulyn.employee.model.EmployeeDataRecord.DataCategory;
import com.regulyn.employee.model.Employee.EmployeeStatus;
import com.regulyn.employee.model.EmployeeRequest.RequestStatus;
import com.regulyn.employee.model.EmployeeRequest.RequestType;
import com.regulyn.employee.service.EmployeeExportService;
import com.regulyn.employee.service.EmployeeRequestService;
import com.regulyn.employee.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeRequestService employeeRequestService;
    private final EmployeeExportService employeeExportService;

    public EmployeeController(EmployeeService employeeService,
                              EmployeeRequestService employeeRequestService,
                              EmployeeExportService employeeExportService) {
        this.employeeService = employeeService;
        this.employeeRequestService = employeeRequestService;
        this.employeeExportService = employeeExportService;
    }

    // ========== Employee Directory ==========

    @PostMapping("/employees")
    public ResponseEntity<EmployeeResponse> createEmployee(@Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeResponse response = employeeService.createEmployee(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/employees")
    public ResponseEntity<List<EmployeeResponse>> listEmployees(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) EmployeeStatus status) {
        List<EmployeeResponse> employees = employeeService.listEmployees(q, status);
        return ResponseEntity.ok(employees);
    }

    // ========== HR Purposes ==========

    @PostMapping("/hr-purposes")
    public ResponseEntity<HRPurposeResponse> createHRPurpose(@Valid @RequestBody CreateHRPurposeRequest request) {
        HRPurposeResponse response = employeeService.createHRPurpose(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/hr-purposes")
    public ResponseEntity<List<HRPurposeResponse>> listHRPurposes() {
        List<HRPurposeResponse> purposes = employeeService.listHRPurposes();
        return ResponseEntity.ok(purposes);
    }

    // ========== Employee Data Records ==========

    @PostMapping("/employee-data-records")
    public ResponseEntity<EmployeeDataRecordResponse> createEmployeeDataRecord(@Valid @RequestBody CreateEmployeeDataRecordRequest request) {
        EmployeeDataRecordResponse response = employeeService.createEmployeeDataRecord(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/employee-data-records")
    public ResponseEntity<List<EmployeeDataRecordResponse>> listEmployeeDataRecords(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) DataCategory dataCategory,
            @RequestParam(required = false) UUID hrPurposeId,
            @RequestParam(required = false) UUID systemId) {
        List<EmployeeDataRecordResponse> records = employeeService.listEmployeeDataRecords(
                employeeId, dataCategory, hrPurposeId, systemId);
        return ResponseEntity.ok(records);
    }

    // ========== Employee Requests (Rights Management) ==========

    @PostMapping("/employee-requests")
    public ResponseEntity<EmployeeRequestResponse> createEmployeeRequest(@Valid @RequestBody CreateEmployeeRequestRequest request) {
        EmployeeRequestResponse response = employeeRequestService.createRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/employee-requests/{id}/assign")
    public ResponseEntity<EmployeeRequestResponse> assignRequest(
            @PathVariable("id") UUID requestId,
            @Valid @RequestBody AssignRequestRequest request) {
        EmployeeRequestResponse response = employeeRequestService.assignRequest(requestId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/employee-requests/{id}/approve")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'DPO', 'REVIEWER')")
    public ResponseEntity<EmployeeRequestResponse> approveRequest(
            @PathVariable("id") UUID requestId,
            @Valid @RequestBody ApproveRequestRequest request) {
        EmployeeRequestResponse response = employeeRequestService.approveRequest(requestId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/employee-requests/{id}/transition")
    public ResponseEntity<EmployeeRequestResponse> transitionRequest(
            @PathVariable("id") UUID requestId,
            @Valid @RequestBody TransitionRequestRequest request) {
        EmployeeRequestResponse response = employeeRequestService.transitionRequest(requestId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/employee-requests/{id}/close")
    public ResponseEntity<EmployeeRequestResponse> closeRequest(
            @PathVariable("id") UUID requestId,
            @Valid @RequestBody CloseRequestRequest request) {
        EmployeeRequestResponse response = employeeRequestService.closeRequest(requestId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/employee-requests/{id}")
    public ResponseEntity<EmployeeRequestResponse> getEmployeeRequest(@PathVariable("id") UUID requestId) {
        EmployeeRequestResponse response = employeeRequestService.getRequest(requestId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/employee-requests")
    public ResponseEntity<Page<EmployeeRequestResponse>> listEmployeeRequests(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) RequestType requestType,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<EmployeeRequestResponse> requests = employeeRequestService.listRequests(
                status, requestType, employeeId, pageable);
        return ResponseEntity.ok(requests);
    }

    // ========== Exports ==========

    @PostMapping("/exports/employee-compliance")
    public ResponseEntity<EmployeeExportResponse> createEmployeeExport(@Valid @RequestBody CreateEmployeeExportRequest request) {
        EmployeeExportResponse response = employeeExportService.createEmployeeExport(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/employee/exports/{id}/download")
    public ResponseEntity<byte[]> downloadEmployeeExport(@PathVariable("id") UUID exportId) {
        byte[] data = employeeExportService.downloadExport(exportId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=employee-export-" + exportId + ".zip")
                .body(data);
    }
}
