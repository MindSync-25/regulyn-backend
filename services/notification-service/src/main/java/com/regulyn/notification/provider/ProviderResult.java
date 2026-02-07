package com.regulyn.notification.provider;

public class ProviderResult {
    private final boolean success;
    private final String providerName;
    private final String providerMessageId;
    private final String failureReason;
    private final boolean retryable;
    
    private ProviderResult(
        boolean success,
        String providerName,
        String providerMessageId,
        String failureReason,
        boolean retryable
    ) {
        this.success = success;
        this.providerName = providerName;
        this.providerMessageId = providerMessageId;
        this.failureReason = failureReason;
        this.retryable = retryable;
    }
    
    public static ProviderResult success(String providerName, String providerMessageId) {
        return new ProviderResult(true, providerName, providerMessageId, null, false);
    }
    
    public static ProviderResult failure(String providerName, String failureReason, boolean retryable) {
        return new ProviderResult(false, providerName, null, failureReason, retryable);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getProviderName() {
        return providerName;
    }
    
    public String getProviderMessageId() {
        return providerMessageId;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
}