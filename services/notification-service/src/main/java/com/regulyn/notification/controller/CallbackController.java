package com.regulyn.notification.controller;

import com.regulyn.notification.dto.DeliveryCallbackRequest;
import com.regulyn.notification.dto.DeliveryCallbackResponse;
import com.regulyn.notification.service.NotificationCallbackService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications/callbacks/smtp")
public class CallbackController {

    private final NotificationCallbackService callbackService;

    public CallbackController(NotificationCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @PostMapping("/email")
    public ResponseEntity<DeliveryCallbackResponse> handleSmtpEmailCallback(
        @Valid @RequestBody DeliveryCallbackRequest request
    ) {
        DeliveryCallbackResponse response = callbackService.handleSmtpEmailCallback(request);
        return ResponseEntity.ok(response);
    }
}
