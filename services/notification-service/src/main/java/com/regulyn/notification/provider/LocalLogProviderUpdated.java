package com.regulyn.notification.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Local logging provider for EMAIL channel.
 * Logs notifications to console instead of sending them.
 * Used for development and testing.
 */
@Component
@ConditionalOnProperty(prefix = "notification.smtp", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalLogProviderUpdated implements NotificationProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalLogProviderUpdated.class);
    
    @Override
    public ProviderSendResult send(String recipientAddress, String subject, String body, String format) {
        logger.info("=".repeat(80));
        logger.info("[LOCAL EMAIL NOTIFICATION]");
        logger.info("To: {}", recipientAddress);
        logger.info("Subject: {}", subject);
        logger.info("Format: {}", format);
        logger.info("-".repeat(80));
        logger.info("Body:\n{}", body);
        logger.info("=".repeat(80));
        
        String messageId = UUID.randomUUID().toString();
        return ProviderSendResult.success(
            messageId,
            "LOCAL_LOG",
            Map.of("logged", "true", "recipientAddress", recipientAddress)
        );
    }
    
    @Override
    public String getChannel() {
        return "EMAIL";
    }
}
