package com.regulyn.notification.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "notification.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationRetryScheduler {

    private final NotificationRetryWorkerService retryWorkerService;

    public NotificationRetryScheduler(NotificationRetryWorkerService retryWorkerService) {
        this.retryWorkerService = retryWorkerService;
    }

    @Scheduled(fixedDelayString = "${notification.retry.fixedDelay:PT30S}")
    public void runRetryCycle() {
        retryWorkerService.runRetryBatch();
    }
}
