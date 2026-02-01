package com.regulyn.notification.controller;

import com.regulyn.notification.dto.SendNotificationRequest;
import com.regulyn.notification.dto.SendNotificationResponse;
import com.regulyn.notification.service.NotificationSendService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications/send")
public class SendController {
    
    private final NotificationSendService sendService;
    
    public SendController(NotificationSendService sendService) {
        this.sendService = sendService;
    }
    
    @PostMapping
    public ResponseEntity<SendNotificationResponse> sendNotification(@Valid @RequestBody SendNotificationRequest request) {
        SendNotificationResponse response = sendService.sendNotification(request);
        return ResponseEntity.ok(response);
    }
}
