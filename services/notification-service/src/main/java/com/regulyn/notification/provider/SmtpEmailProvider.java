package com.regulyn.notification.provider;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * SMTP Email Provider using JavaMail
 * 
 * Configuration properties:
 * - spring.mail.host
 * - spring.mail.port
 * - spring.mail.username
 * - spring.mail.password
 * - spring.mail.properties.mail.smtp.auth
 * - spring.mail.properties.mail.smtp.starttls.enable
 * - notification.smtp.from-address
 * - notification.smtp.from-name
 */
@Component
@ConditionalOnProperty(prefix = "notification.smtp", name = "enabled", havingValue = "true", matchIfMissing = false)
public class SmtpEmailProvider implements NotificationProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(SmtpEmailProvider.class);
    
    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;
    
    public SmtpEmailProvider(
        JavaMailSender mailSender,
        org.springframework.core.env.Environment env
    ) {
        this.mailSender = mailSender;
        this.fromAddress = env.getProperty("notification.smtp.from-address", "noreply@regulyn.com");
        this.fromName = env.getProperty("notification.smtp.from-name", "Regulyn Notifications");
    }
    
    @Override
    public ProviderSendResult send(String recipientAddress, String subject, String body, String format) {
        try {
            String messageId = generateMessageId();
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromAddress, fromName);
            helper.setTo(recipientAddress);
            helper.setSubject(subject);
            
            boolean isHtml = "HTML".equalsIgnoreCase(format);
            helper.setText(body, isHtml);
            
            // Set custom headers for tracking
            message.setHeader("X-Regulyn-Message-ID", messageId);
            message.setHeader("X-Regulyn-Sent-At", Instant.now().toString());
            message.setHeader("Message-ID", "<" + messageId + "@regulyn.com>");
            
            mailSender.send(message);
            
            logger.info("SMTP email sent successfully: messageId={}, recipient={}", messageId, recipientAddress);
            
            return ProviderSendResult.success(
                messageId,
                "SMTP",
                Map.of(
                    "from", fromAddress,
                    "to", recipientAddress,
                    "sentAt", Instant.now().toString()
                )
            );
            
        } catch (MessagingException e) {
            logger.error("Failed to send SMTP email to {}: {}", recipientAddress, e.getMessage(), e);
            return ProviderSendResult.failure(
                "SMTP",
                "MessagingException: " + e.getMessage(),
                "SMTP_MESSAGING_ERROR"
            );
        } catch (Exception e) {
            logger.error("Unexpected error sending SMTP email to {}: {}", recipientAddress, e.getMessage(), e);
            return ProviderSendResult.failure(
                "SMTP",
                "Unexpected error: " + e.getMessage(),
                "SMTP_UNKNOWN_ERROR"
            );
        }
    }
    
    @Override
    public String getChannel() {
        return "EMAIL";
    }
    
    private String generateMessageId() {
        return UUID.randomUUID().toString();
    }
}
