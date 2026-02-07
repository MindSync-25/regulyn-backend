package com.regulyn.retention.api;

import com.regulyn.retention.model.TombstoneCreateRequest;
import com.regulyn.retention.model.TombstoneRemoveRequest;
import com.regulyn.retention.model.TombstoneResponse;
import com.regulyn.retention.service.DeletionTombstoneService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/tombstones")
public class DeletionTombstoneController {

    private final DeletionTombstoneService tombstoneService;

    public DeletionTombstoneController(DeletionTombstoneService tombstoneService) {
        this.tombstoneService = tombstoneService;
    }

    @PostMapping
    public ResponseEntity<TombstoneResponse> createTombstone(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @RequestBody TombstoneCreateRequest request) {

        TombstoneResponse response = tombstoneService.createTombstone(tenantId, userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{tombstoneId}/remove")
    public ResponseEntity<TombstoneResponse> removeTombstone(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID userId,
            @PathVariable("tombstoneId") UUID tombstoneId,
            @RequestBody TombstoneRemoveRequest request) {

        TombstoneResponse response = tombstoneService.removeTombstone(tenantId, userId, tombstoneId, request);
        return ResponseEntity.ok(response);
    }
}