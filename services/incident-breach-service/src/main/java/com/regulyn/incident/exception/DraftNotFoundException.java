package com.regulyn.incident.exception;

public class DraftNotFoundException extends IllegalArgumentException {
    public DraftNotFoundException(String message) {
        super(message);
    }
}
