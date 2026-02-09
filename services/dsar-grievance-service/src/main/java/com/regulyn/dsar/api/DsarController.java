package com.regulyn.dsar.api;

import com.regulyn.dsar.model.*;
import com.regulyn.dsar.service.DsarWorkflowService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/dsar")
public class DsarController {
  
  private final DsarWorkflowService dsarWorkflowService;
  
  public DsarController(DsarWorkflowService dsarWorkflowService) {
    this.dsarWorkflowService = dsarWorkflowService;
  }
  
  @PostMapping
  public CreateDsarResponse createDsar(@Valid @RequestBody CreateDsarRequest request) {
    return dsarWorkflowService.createDsar(request);
  }
  
  @PostMapping("/{dsarId}/assign")
  public AssignDsarResponse assignDsar(
      @PathVariable("dsarId") UUID dsarId,
      @Valid @RequestBody AssignDsarRequest request) {
    return dsarWorkflowService.assignDsar(dsarId, request);
  }
  
  @PostMapping("/{dsarId}/transition")
  public TransitionDsarResponse transitionStatus(
      @PathVariable("dsarId") UUID dsarId,
      @Valid @RequestBody TransitionDsarRequest request) {
    return dsarWorkflowService.transitionStatus(dsarId, request);
  }
  
  @PostMapping("/{dsarId}/approve")
  public ApproveDsarResponse approveDsar(
      @PathVariable("dsarId") UUID dsarId,
      @Valid @RequestBody ApproveDsarRequest request) {
    return dsarWorkflowService.approveDsar(dsarId, request);
  }
  
  @PostMapping("/{dsarId}/close")
  public CloseDsarResponse closeDsar(
      @PathVariable("dsarId") UUID dsarId,
      @RequestBody CloseDsarRequest request,
      @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
    return dsarWorkflowService.closeDsar(dsarId, request, idempotencyKey);
  }
  
  @GetMapping("/{dsarId}")
  public DsarDetailResponse getDsar(@PathVariable("dsarId") UUID dsarId) {
    return dsarWorkflowService.getDsar(dsarId);
  }
  
  @GetMapping
  public Page<DsarDetailResponse> searchDsars(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String requestType,
      @RequestParam(required = false) UUID dataPrincipalId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return dsarWorkflowService.searchDsars(status, requestType, dataPrincipalId, page, size);
  }
}
