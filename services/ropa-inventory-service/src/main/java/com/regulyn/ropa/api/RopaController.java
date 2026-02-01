package com.regulyn.ropa.api;

import com.regulyn.ropa.api.dto.*;
import com.regulyn.ropa.model.*;
import com.regulyn.ropa.service.RopaService;
import com.regulyn.ropa.service.RopaExportService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping
public class RopaController {

    private final RopaService ropaService;
    private final RopaExportService ropaExportService;

    public RopaController(RopaService ropaService, RopaExportService ropaExportService) {
        this.ropaService = ropaService;
        this.ropaExportService = ropaExportService;
    }

    // ========== SYSTEMS ==========

    @PostMapping("/systems")
    public ResponseEntity<SystemResponse> createSystem(@Valid @RequestBody CreateSystemRequest request) {
        SystemResponse response = ropaService.createSystem(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/systems")
    public ResponseEntity<List<RopaSystem>> listSystems(
        @RequestParam(required = false) RopaSystem.SystemType type,
        @RequestParam(required = false) RopaSystem.Criticality criticality,
        @RequestParam(required = false) String q
    ) {
        List<RopaSystem> systems = ropaService.listSystems(type, criticality, q);
        return ResponseEntity.ok(systems);
    }

    @PostMapping("/systems/{systemId}/disable")
    public ResponseEntity<SystemResponse> disableSystem(@PathVariable UUID systemId) {
        SystemResponse response = ropaService.disableSystem(systemId);
        return ResponseEntity.ok(response);
    }

    // ========== DATA CATEGORIES ==========

    @PostMapping("/data-categories")
    public ResponseEntity<DataCategoryResponse> createDataCategory(@Valid @RequestBody CreateDataCategoryRequest request) {
        DataCategoryResponse response = ropaService.createDataCategory(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/data-categories")
    public ResponseEntity<List<RopaDataCategory>> listDataCategories() {
        List<RopaDataCategory> categories = ropaService.listDataCategories();
        return ResponseEntity.ok(categories);
    }

    // ========== PROCESSING ACTIVITIES ==========

    @PostMapping("/activities")
    public ResponseEntity<ActivityResponse> createActivity(@Valid @RequestBody CreateActivityRequest request) {
        ActivityResponse response = ropaService.createActivity(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/activities/{activityId}/link")
    public ResponseEntity<LinkActivityResponse> linkActivity(
        @PathVariable UUID activityId,
        @Valid @RequestBody LinkActivityRequest request
    ) {
        LinkActivityResponse response = ropaService.linkActivity(activityId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/activities/{activityId}/publish")
    public ResponseEntity<ActivityResponse> publishActivity(@PathVariable UUID activityId) {
        ActivityResponse response = ropaService.publishActivity(activityId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/activities/{activityId}/versions")
    public ResponseEntity<CreateVersionResponse> createVersion(
        @PathVariable UUID activityId,
        @RequestBody CreateVersionRequest request
    ) {
        CreateVersionResponse response = ropaService.createNewVersion(activityId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/activities/{activityId}")
    public ResponseEntity<Map<String, Object>> getActivity(@PathVariable UUID activityId) {
        Map<String, Object> activity = ropaService.getActivity(activityId);
        return ResponseEntity.ok(activity);
    }

    @GetMapping("/activities")
    public ResponseEntity<Page<RopaActivityVersion>> listActivities(
        @RequestParam(required = false) RopaActivityVersion.Status status,
        @RequestParam(required = false) RopaActivityVersion.RiskLevel riskLevel,
        @RequestParam(required = false) RopaActivityVersion.LawfulBasis lawfulBasis,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) UUID systemId,
        @RequestParam(required = false) UUID dataCategoryId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<RopaActivityVersion> activities = ropaService.listActivities(
            status, riskLevel, lawfulBasis, q, systemId, dataCategoryId, page, size);
        return ResponseEntity.ok(activities);
    }

    // ========== EXPORTS ==========

    @PostMapping("/exports/ropa")
    public ResponseEntity<RopaExportResponse> createRopaExport(@RequestBody CreateRopaExportRequest request) {
        RopaExportResponse response = ropaExportService.createRopaExport(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ropa/exports/{exportId}/download")
    public ResponseEntity<byte[]> downloadRopaExport(@PathVariable UUID exportId) {
        byte[] data = ropaExportService.downloadRopaExport(exportId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.builder("attachment")
            .filename("ropa-export-" + exportId + ".zip")
            .build());

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }
}
