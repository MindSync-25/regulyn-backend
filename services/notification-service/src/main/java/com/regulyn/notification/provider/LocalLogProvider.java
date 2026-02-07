package com.regulyn.notification.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Local logging provider for EMAIL channel.
 * Logs notifications to console instead of sending them.
 * Used for development and testing.
 */
@Component
@ConditionalOnMissingBean(NotificationProvider.class)
public class LocalLogProvider implements NotificationProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalLogProvider.class);
    
    @Override
    public ProviderResult sendEmail(EmailSendCommand command) {
        logger.info("=".repeat(80));
        logger.info("[LOCAL EMAIL NOTIFICATION]");
        logger.info("To: {}", command.recipientAddress());
        logger.info("Subject: {}", command.subject());
        logger.info("Format: {}", command.format());
        logger.info("Body length: {}", command.body() != null ? command.body().length() : 0);
        logger.info("-".repeat(80));
        logger.info("=".repeat(80));
        
        return ProviderResult.success(getProviderName(), null);
    }
    
    @Override
    public String getChannel() {
        return "EMAIL";
    }
    
    @Override
    public String getProviderName() {
        return "LOCAL_LOG";
    }
}
