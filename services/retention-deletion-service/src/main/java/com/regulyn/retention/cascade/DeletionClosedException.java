package com.regulyn.retention.cascade;

public class DeletionClosedException extends RuntimeException {
    public DeletionClosedException(String message) {
        super(message);
    }
}
