package com.regulyn.notification.scheduler;

import com.regulyn.notification.entity.NotificationMessage;
import com.regulyn.notification.provider.NotificationProvider;
import com.regulyn.notification.provider.ProviderSendResult;
import com.regulyn.notification.repository.NotificationMessageRepository;
import com.regulyn.notification.util.NotificationAuditHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Scheduler for retrying failed notification messages with exponential backoff
 * 
 * Retry Strategy:
 * - Attempt 1: Immediate
 * - Attempt 2: 1 minute after first failure
 * - Attempt 3: 5 minutes after second failure  
 * - Attempt 4+: 15 minutes after previous failure
 * 
 * Max attempts: 3 (configurable per message)
 * 
 * Runs every minute to check for messages ready for retry
 */
@Component
public class NotificationRetryScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationRetryScheduler.class);
    
    private static final int BASE_DELAY_SECONDS = 60;  // 1 minute
    private static final int MAX_DELAY_SECONDS = 900;  // 15 minutes
    
    private final NotificationMessageRepository messageRepository;
    private final List<NotificationProvider> providers;
    private final NotificationAuditHelper auditHelper;
    
    public NotificationRetryScheduler(
        NotificationMessageRepository messageRepository,
        List<NotificationProvider> providers,
        NotificationAuditHelper auditHelper
    ) {
        this.messageRepository = messageRepository;
        this.providers = providers;
        this.auditHelper = auditHelper;
    }
    
    /**
     * Scheduled task to retry failed messages
     * Runs every minute
     */
    @Scheduled(fixedRate = 60000, initialDelay = 10000) // Every 60 seconds, start after 10 seconds
    @Transactional
    public void retryFailedMessages() {
        try {
            List<NotificationMessage> retryMessages = messageRepository.findMessagesReadyForRetry(Instant.now());
            
            if (retryMessages.isEmpty()) {
                logger.trace("No messages ready for retry");
                return;
            }
            
            logger.info("Found {} messages ready for retry", retryMessages.size());
            
            for (NotificationMessage message : retryMessages) {
                try {
                    retryMessage(message);
                } catch (Exception e) {
                    logger.error("Error retrying message {}: {}", message.getMessageId(), e.getMessage(), e);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error in retry scheduler: {}", e.getMessage(), e);
        }
    }
    
    private void retryMessage(NotificationMessage message) {
        logger.info("Retrying message: messageId={}, attempt={}/{}, channel={}", 
            message.getMessageId(), message.getAttemptCount() + 1, message.getMaxAttempts(), message.getChannel());
        
        // Find provider for channel
        NotificationProvider provider = providers.stream()
            .filter(p -> p.getChannel().equalsIgnoreCase(message.getChannel()))
            .findFirst()
            .orElse(null);
        
        if (provider == null) {
            logger.error("No provider found for channel: {}", message.getChannel());
            message.setStatus(NotificationMessage.MessageStatus.FAILED_TERMINAL);
            message.setLastErrorMessage("No provider available for channel: " + message.getChannel());
            message.setLastErrorCode("NO_PROVIDER");
            messageRepository.save(message);
            return;
        }
        
        // Increment attempt count
        message.setAttemptCount(message.getAttemptCount() + 1);
        message.setLastAttemptAt(Instant.now());
        
        // Send via provider
        ProviderSendResult result = provider.send(
            message.getRecipientAddress(),
            message.getMessageSubject(),
            message.getMessageBody(),
            message.getMessageFormat()
        );
        
        if (result.success()) {
            // Success - update message status
            message.setStatus(NotificationMessage.MessageStatus.SENT);
            message.setProviderMessageId(result.providerMessageId());
            message.setProviderName(result.providerName());
            message.setNextRetryAt(null);
            message.setLastErrorMessage(null);
            message.setLastErrorCode(null);
            
            logger.info("Message retry successful: messageId={}, providerMessageId={}", 
                message.getMessageId(), result.providerMessageId());
            
            // Audit event
            auditHelper.writeAudit(
                "NOTIFICATION_RETRY_SUCCESS",
                "NotificationMessage",
                message.getMessageId().toString(),
                Map.of(
                    "attemptCount", (Object) message.getAttemptCount(),
                    "providerMessageId", (Object) result.providerMessageId()
                )
            );
            
            // Outbox event
            auditHelper.writeOutbox(
                "NOTIFICATION_SENT_RETRY",
                "notification.sent.retry.v1",
                message.getMessageId().toString(),
                Map.of(
                    "messageId", (Object) message.getMessageId().toString(),
                    "recipientAddress", (Object) message.getRecipientAddress(),
                    "channel", (Object) message.getChannel(),
                    "attemptCount", (Object) message.getAttemptCount(),
                    "providerMessageId", (Object) result.providerMessageId()
                )
            );
            
        } else {
            // Failure - determine if retryable
            message.setLastErrorMessage(result.errorMessage());
            message.setLastErrorCode(result.errorCode());
            
            if (message.getAttemptCount() >= message.getMaxAttempts()) {
                // Max attempts reached - terminal failure
                message.setStatus(NotificationMessage.MessageStatus.FAILED_TERMINAL);
                message.setNextRetryAt(null);
                
                logger.warn("Message retry failed - max attempts reached: messageId={}, error={}", 
                    message.getMessageId(), result.errorMessage());
                
                // Outbox event for terminal failure
                auditHelper.writeOutbox(
                    "NOTIFICATION_FAILED_TERMINAL",
                    "notification.failed.terminal.v1",
                    message.getMessageId().toString(),
                    Map.of(
                        "messageId", (Object) message.getMessageId().toString(),
                        "recipientAddress", (Object) message.getRecipientAddress(),
                        "channel", (Object) message.getChannel(),
                        "attemptCount", (Object) message.getAttemptCount(),
                        "errorMessage", (Object) result.errorMessage()
                    )
                );
                
            } else {
                // Schedule next retry with exponential backoff
                Duration backoff = calculateBackoff(message.getAttemptCount());
                message.setNextRetryAt(Instant.now().plus(backoff));
                message.setStatus(NotificationMessage.MessageStatus.FAILED_RETRYABLE);
                
                logger.info("Message retry failed - will retry: messageId={}, nextRetry={}, error={}", 
                    message.getMessageId(), message.getNextRetryAt(), result.errorMessage());
            }
            
            // Audit event
            auditHelper.writeAudit(
                "NOTIFICATION_RETRY_FAILED",
                "NotificationMessage",
                message.getMessageId().toString(),
                Map.of(
                    "attemptCount", (Object) message.getAttemptCount(),
                    "errorMessage", (Object) result.errorMessage(),
                    "errorCode", (Object) (result.errorCode() != null ? result.errorCode() : ""),
                    "status", (Object) message.getStatus().toString()
                )
            );
        }
        
        messageRepository.save(message);
    }
    
    /**
     * Calculate exponential backoff delay
     * Attempt 1: 60s (1 min)
     * Attempt 2: 300s (5 min)
     * Attempt 3+: 900s (15 min)
     */
    private Duration calculateBackoff(int attemptCount) {
        if (attemptCount == 1) {
            return Duration.ofSeconds(BASE_DELAY_SECONDS); // 1 minute
        } else if (attemptCount == 2) {
            return Duration.ofSeconds(BASE_DELAY_SECONDS * 5); // 5 minutes
        } else {
            return Duration.ofSeconds(MAX_DELAY_SECONDS); // 15 minutes
        }
    }
}
