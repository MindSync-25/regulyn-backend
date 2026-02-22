package com.regulyn.notification.controller;

import com.regulyn.notification.dto.GetPreferencesResponse;
import com.regulyn.notification.dto.OptOutRequest;
import com.regulyn.notification.dto.OptOutResponse;
import com.regulyn.notification.service.PreferenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications/preferences")
public class PreferenceController {
    
    private final PreferenceService preferenceService;
    
    public PreferenceController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }
    
    @PostMapping
    public ResponseEntity<OptOutResponse> updatePreference(@Valid @RequestBody OptOutRequest request) {
        OptOutResponse response = preferenceService.updatePreference(request);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{dataPrincipalId}")
    public ResponseEntity<GetPreferencesResponse> getPreferences(
            @PathVariable("dataPrincipalId") String dataPrincipalId) {
        GetPreferencesResponse response = preferenceService.getPreferences(dataPrincipalId);
        return ResponseEntity.ok(response);
    }
}
