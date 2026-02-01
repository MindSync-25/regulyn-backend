package com.regulyn.nominee.controller;

import com.regulyn.nominee.dto.RegisterNomineeRequest;
import com.regulyn.nominee.dto.NomineeResponse;
import com.regulyn.nominee.dto.VerifyNomineeRequest;
import com.regulyn.nominee.service.NomineeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/nominees")
public class NomineeController {

    private final NomineeService nomineeService;

    public NomineeController(NomineeService nomineeService) {
        this.nomineeService = nomineeService;
    }

    @PostMapping
    @PreAuthorize("hasRole('DATA_PRINCIPAL')")
    public ResponseEntity<NomineeResponse> registerNominee(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID dataPrincipalId,
            @Valid @RequestBody RegisterNomineeRequest request) {
        NomineeResponse response = nomineeService.registerNominee(tenantId, dataPrincipalId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NomineeResponse> verifyNominee(
            @PathVariable("id") UUID nomineeId,
            @Valid @RequestBody VerifyNomineeRequest request) {
        NomineeResponse response = nomineeService.verifyNominee(nomineeId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> disableNominee(
            @PathVariable("id") UUID nomineeId,
            @RequestHeader("X-User-ID") UUID userId) {
        nomineeService.disableNominee(nomineeId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DATA_PRINCIPAL', 'ADMIN')")
    public ResponseEntity<NomineeResponse> getNominee(@PathVariable("id") UUID nomineeId) {
        NomineeResponse response = nomineeService.getNominee(nomineeId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('DATA_PRINCIPAL')")
    public ResponseEntity<List<NomineeResponse>> getNomineesByPrincipal(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-User-ID") UUID dataPrincipalId) {
        List<NomineeResponse> responses = nomineeService.getNomineesByPrincipal(tenantId, dataPrincipalId);
        return ResponseEntity.ok(responses);
    }
}
