package com.regulyn.guardian.api;

import com.regulyn.guardian.esign.ProviderId;
import com.regulyn.guardian.service.EsignWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/esign")
public class EsignWebhookController {

    private final EsignWebhookService webhookService;

    public EsignWebhookController(EsignWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/webhooks/{provider}")
    public ResponseEntity<String> handleWebhook(
            @PathVariable("provider") String provider,
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            HttpServletRequest request) throws IOException {
        ProviderId providerId;
        try {
            providerId = ProviderId.fromString(provider);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("unsupported_provider");
        }
        byte[] rawBody = StreamUtils.copyToByteArray(request.getInputStream());

        Map<String, String> headers = new HashMap<>();
        String stubSignature = request.getHeader("X-Stub-Signature");
        if (stubSignature != null) {
            headers.put("X-Stub-Signature", stubSignature);
        }

        EsignWebhookService.WebhookResult result = webhookService.handleWebhook(tenantId, providerId, headers, rawBody);

        if (result.signatureFailed()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body("signature_failed");
        }

        if (result.processed()) {
            return ResponseEntity.ok("processed");
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body("accepted");
    }
}
