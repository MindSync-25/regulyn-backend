package com.regulyn.notification.provider;

public interface NotificationProvider {
    
    /**
     * Send notification to recipient with enhanced result tracking
     * @param recipientAddress Email address, phone number, or WhatsApp ID
     * @param subject Message subject (for email)
     * @param body Message body
     * @param format TEXT or HTML
     * @return ProviderSendResult with success status, provider message ID, and metadata
     */
    ProviderSendResult send(String recipientAddress, String subject, String body, String format);
    
    /**
     * Get provider channel type
     * @return EMAIL, SMS, or WHATSAPP
     */
    String getChannel();
}
