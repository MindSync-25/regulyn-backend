package io.regulyn.connector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Duration;

/**
 * Configuration properties for AWS Secrets Manager integration.
 */
@Configuration
@ConfigurationProperties(prefix = "aws.secrets")
public class AwsSecretsConfig {
    
    /**
     * AWS region for Secrets Manager
     */
    private String region = "us-east-1";
    
    /**
     * Optional endpoint override for LocalStack or custom endpoint
     */
    private URI endpointOverride;
    
    /**
     * Cache TTL in seconds
     */
    private int cacheTtlSeconds = 300; // 5 minutes default
    
    /**
     * Fail closed: if true, throw exception when AWS resolution fails.
     * If false, allow fallback to local DB encrypted credentials.
     */
    private boolean failClosed = true;
    
    /**
     * Enable AWS Secrets Manager integration
     */
    private boolean enabled = false;
    
    public String getRegion() {
        return region;
    }
    
    public void setRegion(String region) {
        this.region = region;
    }
    
    public URI getEndpointOverride() {
        return endpointOverride;
    }
    
    public void setEndpointOverride(URI endpointOverride) {
        this.endpointOverride = endpointOverride;
    }
    
    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }
    
    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }
    
    public boolean isFailClosed() {
        return failClosed;
    }
    
    public void setFailClosed(boolean failClosed) {
        this.failClosed = failClosed;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Duration getCacheTtl() {
        return Duration.ofSeconds(cacheTtlSeconds);
    }
}
