package com.regulyn.notification.service;

public record ConsentDecision(Outcome outcome, String reasonCode, boolean bypassedDueToLegal) {

    public enum Outcome {
        ALLOW,
        BLOCK,
        CHECK_FAILED_ALLOW,
        CHECK_FAILED_BLOCK
    }
}
