package com.regulyn.notification.provider;

public interface NotificationProvider {
    
    /**
     * Send email notification.
     */
    ProviderResult sendEmail(EmailSendCommand command);
    
    /**
     * Optional SMS support.
     */
    default ProviderResult sendSms(Object command) {
        throw new UnsupportedOperationException("SMS not supported by this provider");
    }
    
    /**
     * Optional WhatsApp support.
     */
    default ProviderResult sendWhatsapp(Object command) {
        throw new UnsupportedOperationException("WhatsApp not supported by this provider");
    }
    
    /**
     * Get provider channel type.
     * @return EMAIL, SMS, or WHATSAPP
     */
    String getChannel();
    
    /**
     * Provider name, e.g. SMTP.
     */
    String getProviderName();
}
