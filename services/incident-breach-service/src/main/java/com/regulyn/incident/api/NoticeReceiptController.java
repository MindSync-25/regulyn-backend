package com.regulyn.incident.api;

import com.regulyn.incident.config.NotificationContactsProperties;
import com.regulyn.incident.dto.NoticeReceiptRequest;
import com.regulyn.incident.entity.NoticeDispatchLogEntity;
import com.regulyn.incident.service.NoticeReceiptService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/incidents/notifications/receipts")
public class NoticeReceiptController {

    private final NoticeReceiptService receiptService;
    private final NotificationContactsProperties contactsProperties;

    public NoticeReceiptController(NoticeReceiptService receiptService,
                                   NotificationContactsProperties contactsProperties) {
        this.receiptService = receiptService;
        this.contactsProperties = contactsProperties;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> ingestReceipt(
            @Valid @RequestBody NoticeReceiptRequest request,
            @RequestHeader(value = "X-Webhook-Secret", required = false) String webhookSecret) {
        String expected = contactsProperties.getReceiptWebhookSecret();
        if (expected == null || expected.isBlank() || webhookSecret == null || !expected.equals(webhookSecret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook secret");
        }

        NoticeDispatchLogEntity updated = receiptService.ingestReceipt(request);
        return ResponseEntity.ok(Map.of(
                "dispatchLogId", updated.getId().toString(),
                "status", updated.getStatus()
        ));
    }
}
