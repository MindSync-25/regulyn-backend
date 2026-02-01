package com.regulyn.notification.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local logging provider for EMAIL channel.
 * Logs notifications to console instead of sending them.
 * Used for development and testing.
 */
@Component
public class LocalLogProvider implements NotificationProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalLogProvider.class);
    
    @Override
    public boolean send(String recipientAddress, String subject, String body, String format) {
        logger.info("=".repeat(80));
        logger.info("[LOCAL EMAIL NOTIFICATION]");
        logger.info("To: {}", recipientAddress);
        logger.info("Subject: {}", subject);
        logger.info("Format: {}", format);
        logger.info("-".repeat(80));
        logger.info("Body:\n{}", body);
        logger.info("=".repeat(80));
        
        return true; // Always succeeds
    }
    
    @Override
    public String getChannel() {
        return "EMAIL";
    }
}
