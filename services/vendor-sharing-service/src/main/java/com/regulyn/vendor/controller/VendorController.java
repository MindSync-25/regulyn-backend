package com.regulyn.vendor.controller;

import com.regulyn.vendor.dto.*;
import com.regulyn.vendor.model.Vendor;
import com.regulyn.vendor.service.VendorExportService;
import com.regulyn.vendor.service.VendorService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/vendor")
public class VendorController {

    private final VendorService vendorService;
    private final VendorExportService vendorExportService;

    public VendorController(
        VendorService vendorService,
        VendorExportService vendorExportService
    ) {
        this.vendorService = vendorService;
        this.vendorExportService = vendorExportService;
    }

    /**
     * 1. POST /vendors - Create vendor
     */
    @PostMapping("/vendors")
    public ResponseEntity<VendorResponse> createVendor(@Valid @RequestBody CreateVendorRequest request) {
        VendorResponse response = vendorService.createVendor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 2. GET /vendors - List vendors with filters
     */
    @GetMapping("/vendors")
    public ResponseEntity<List<VendorResponse>> listVendors(
        @RequestParam(name = "enabled", required = false) Boolean enabled,
        @RequestParam(name = "riskLevel", required = false) Vendor.RiskLevel riskLevel,
        @RequestParam(name = "q", required = false) String q
    ) {
        List<VendorResponse> vendors = vendorService.listVendors(enabled, riskLevel, q);
        return ResponseEntity.ok(vendors);
    }

    /**
     * 3. POST /vendors/{vendorId}/disable - Disable vendor
     */
    @PostMapping("/vendors/{vendorId}/disable")
    public ResponseEntity<VendorResponse> disableVendor(@PathVariable UUID vendorId) {
        VendorResponse response = vendorService.disableVendor(vendorId);
        return ResponseEntity.ok(response);
    }

    /**
     * 4. POST /vendors/{vendorId}/agreements - Create agreement
     */
    @PostMapping("/vendors/{vendorId}/agreements")
    public ResponseEntity<AgreementResponse> createAgreement(
        @PathVariable UUID vendorId,
        @Valid @RequestBody CreateAgreementRequest request
    ) {
        AgreementResponse response = vendorService.createAgreement(vendorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 5. GET /vendors/{vendorId}/agreements - List agreements
     */
    @GetMapping("/vendors/{vendorId}/agreements")
    public ResponseEntity<List<AgreementResponse>> listAgreements(@PathVariable UUID vendorId) {
        List<AgreementResponse> agreements = vendorService.listAgreements(vendorId);
        return ResponseEntity.ok(agreements);
    }

    /**
     * 6. POST /sharing-records - Create sharing record
     */
    @PostMapping("/sharing-records")
    public ResponseEntity<SharingRecordResponse> createSharingRecord(
        @Valid @RequestBody CreateSharingRecordRequest request
    ) {
        SharingRecordResponse response = vendorService.createSharingRecord(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 7. POST /sharing-records/{sharingId}/disable - Disable sharing record
     */
    @PostMapping("/sharing-records/{sharingId}/disable")
    public ResponseEntity<DisableSharingResponse> disableSharingRecord(@PathVariable UUID sharingId) {
        DisableSharingResponse response = vendorService.disableSharingRecord(sharingId);
        return ResponseEntity.ok(response);
    }

    /**
     * 8. GET /sharing-records - List sharing records with pagination and filters
     */
    @GetMapping("/sharing-records")
    public ResponseEntity<Page<SharingRecordResponse>> listSharingRecords(
        @RequestParam(name = "vendorId", required = false) UUID vendorId,
        @RequestParam(name = "activityId", required = false) UUID activityId,
        @RequestParam(name = "systemId", required = false) UUID systemId,
        @RequestParam(name = "enabled", required = false) Boolean enabled,
        @RequestParam(name = "dataCategory", required = false) String dataCategory,
        @RequestParam(name = "transferCrossBorder", required = false) Boolean transferCrossBorder,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SharingRecordResponse> records = vendorService.listSharingRecords(
            vendorId, activityId, systemId, enabled, dataCategory, transferCrossBorder, pageable
        );
        return ResponseEntity.ok(records);
    }

    /**
     * 9. GET /sharing-records/{sharingId} - Get sharing record detail
     */
    @GetMapping("/sharing-records/{sharingId}")
    public ResponseEntity<SharingRecordDetailResponse> getSharingRecord(@PathVariable UUID sharingId) {
        SharingRecordDetailResponse response = vendorService.getSharingRecord(sharingId);
        return ResponseEntity.ok(response);
    }

    /**
     * 10. POST /exports/vendor-sharing - Create vendor-sharing export
     */
    @PostMapping("/exports/vendor-sharing")
    public ResponseEntity<VendorExportResponse> createVendorExport(
        @RequestBody CreateVendorExportRequest request
    ) {
        VendorExportResponse response = vendorExportService.createVendorExport(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 11. GET /exports/{exportId}/download - Download export
     */
    @GetMapping("/exports/{exportId}/download")
    public ResponseEntity<byte[]> downloadVendorExport(@PathVariable UUID exportId) {
        byte[] data = vendorExportService.downloadVendorExport(exportId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "vendor-export-" + exportId + ".zip");
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(data);
    }
}
