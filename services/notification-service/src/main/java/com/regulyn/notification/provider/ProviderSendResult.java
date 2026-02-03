package com.regulyn.notification.provider;

import java.util.Map;

/**
 * Result returned by NotificationProvider after send attempt
 */
public record ProviderSendResult(
    boolean success,
    String providerMessageId,    // Provider's message ID for tracking
    String providerName,          // Provider name (SMTP, SES, etc.)
    String errorMessage,          // Error message if failed
    String errorCode,             // Error code if failed
    Map<String, String> metadata  // Additional provider-specific metadata
) {
    
    public static ProviderSendResult success(String providerMessageId, String providerName, Map<String, String> metadata) {
        return new ProviderSendResult(true, providerMessageId, providerName, null, null, metadata);
    }
    
    public static ProviderSendResult failure(String providerName, String errorMessage, String errorCode) {
        return new ProviderSendResult(false, null, providerName, errorMessage, errorCode, Map.of());
    }
}
