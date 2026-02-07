package com.regulyn.notification.provider;

import com.regulyn.notification.config.SmtpProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(JavaMailSender.class)
@ConditionalOnProperty(prefix = "smtp", name = "enabled", havingValue = "true")
public class SmtpEmailProvider implements NotificationProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(SmtpEmailProvider.class);
    
    private final JavaMailSender mailSender;
    private final SmtpProperties smtpProperties;
    
    public SmtpEmailProvider(JavaMailSender mailSender, SmtpProperties smtpProperties) {
        this.mailSender = mailSender;
        this.smtpProperties = smtpProperties;
    }
    
    @Override
    public ProviderResult sendEmail(EmailSendCommand command) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            
            String from = smtpProperties.getFrom() != null ? smtpProperties.getFrom() : smtpProperties.getUsername();
            helper.setFrom(from);
            helper.setTo(command.recipientAddress());
            helper.setSubject(command.subject());
            boolean isHtml = command.format() != null && command.format().equalsIgnoreCase("HTML");
            helper.setText(command.body(), isHtml);
            
            mailSender.send(mimeMessage);
            String messageId = mimeMessage.getMessageID();
            
            return ProviderResult.success(getProviderName(), messageId);
        } catch (MailAuthenticationException e) {
            logger.warn("SMTP authentication failed: {}", e.getMessage());
            return ProviderResult.failure(getProviderName(), "SMTP authentication failed", false);
        } catch (MailSendException e) {
            logger.warn("SMTP send failed: {}", e.getMessage());
            return ProviderResult.failure(getProviderName(), "SMTP send failed", true);
        } catch (MailException | MessagingException e) {
            logger.warn("SMTP error: {}", e.getMessage());
            return ProviderResult.failure(getProviderName(), "SMTP error", true);
        }
    }
    
    @Override
    public String getChannel() {
        return "EMAIL";
    }
    
    @Override
    public String getProviderName() {
        return "SMTP";
    }
}