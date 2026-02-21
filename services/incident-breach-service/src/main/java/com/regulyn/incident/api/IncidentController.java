package com.regulyn.incident.api;

import com.regulyn.incident.dto.*;
import com.regulyn.incident.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/incidents")
public class IncidentController {
    
    private final IncidentService incidentService;
    
    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }
    
    @PostMapping
    public ResponseEntity<CreateIncidentResponse> createIncident(@Valid @RequestBody CreateIncidentRequest request) {
        CreateIncidentResponse response = incidentService.createIncident(request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{incidentId}/tasks")
    public ResponseEntity<CreateTaskResponse> createTask(
            @PathVariable("incidentId") UUID incidentId,
            @Valid @RequestBody CreateTaskRequest request) {
        CreateTaskResponse response = incidentService.createTask(incidentId, request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{incidentId}/transition")
    public ResponseEntity<TransitionResponse> transitionStatus(
            @PathVariable("incidentId") UUID incidentId,
            @Valid @RequestBody TransitionRequest request) {
        TransitionResponse response = incidentService.transitionStatus(incidentId, request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{incidentId}/notifications/draft")
    public ResponseEntity<DraftNotificationResponse> draftNotification(
            @PathVariable("incidentId") UUID incidentId,
            @Valid @RequestBody DraftNotificationRequest request) {
        DraftNotificationResponse response = incidentService.draftNotification(incidentId, request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{incidentId}/notifications/{notificationId}/approve")
    public ResponseEntity<ApproveNotificationResponse> approveNotification(
            @PathVariable("incidentId") UUID incidentId,
            @PathVariable("notificationId") UUID notificationId,
            @RequestBody ApproveNotificationRequest request) {
        ApproveNotificationResponse response = incidentService.approveNotification(incidentId, notificationId, request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{incidentId}/notifications/{notificationId}/send")
    public ResponseEntity<SendNotificationResponse> sendNotification(
            @PathVariable("incidentId") UUID incidentId,
            @PathVariable("notificationId") UUID notificationId) {
        SendNotificationResponse response = incidentService.sendNotification(incidentId, notificationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{incidentId}/notifications/{notificationId}/reject")
    public ResponseEntity<ApproveNotificationResponse> rejectNotification(
            @PathVariable("incidentId") UUID incidentId,
            @PathVariable("notificationId") UUID notificationId,
            @RequestBody ApproveNotificationRequest request) {
        ApproveNotificationResponse response = incidentService.rejectNotification(incidentId, notificationId, request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{incidentId}/close")
    public ResponseEntity<CloseIncidentResponse> closeIncident(
            @PathVariable("incidentId") UUID incidentId,
            @RequestBody CloseIncidentRequest request) {
        CloseIncidentResponse response = incidentService.closeIncident(incidentId, request);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{incidentId}")
    public ResponseEntity<IncidentDetailsResponse> getIncident(@PathVariable("incidentId") UUID incidentId) {
        IncidentDetailsResponse response = incidentService.getIncidentDetails(incidentId);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    public ResponseEntity<Page<IncidentDetailsResponse>> listIncidents(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "openedAt"));
        Page<IncidentDetailsResponse> response = incidentService.listIncidents(status, severity, pageable);
        return ResponseEntity.ok(response);
    }
}
