package io.regulyn.connector.webhook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response for webhook ingestion.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookResponse {
    
    private String status;
    private String message;
    private String correlationId;

    public WebhookResponse() {
    }

    public WebhookResponse(String status, String message, String correlationId) {
        this.status = status;
        this.message = message;
        this.correlationId = correlationId;
    }

    public static WebhookResponse accepted(String correlationId) {
        return new WebhookResponse("accepted", "Webhook received successfully", correlationId);
    }

    public static WebhookResponse duplicate(String correlationId) {
        return new WebhookResponse("duplicate", "Duplicate webhook ignored", correlationId);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for WebhookResponse.
     */
    public static class Builder {
        private String status;
        private String message;
        private String correlationId;

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public WebhookResponse build() {
            return new WebhookResponse(status, message, correlationId);
        }
    }

    // Getters and Setters

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}
