package com.regulyn.retention.connector;

public class ConnectorServiceUnavailableException extends RuntimeException {
    public ConnectorServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
