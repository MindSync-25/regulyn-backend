package com.regulyn.retention.cascade;

public class DeletionNotFoundException extends RuntimeException {
    public DeletionNotFoundException(String message) {
        super(message);
    }
}
