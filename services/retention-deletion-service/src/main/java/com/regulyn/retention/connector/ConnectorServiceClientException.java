package com.regulyn.retention.connector;

public class ConnectorServiceClientException extends RuntimeException {
    private final int statusCode;

    public ConnectorServiceClientException(String message, Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
