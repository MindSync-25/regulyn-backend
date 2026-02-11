package com.regulyn.nominee.exception;

import com.regulyn.nominee.dto.MissingStep;

import java.util.List;

public class VerificationGateException extends RuntimeException {

    private final List<MissingStep> missingSteps;
    private final boolean exceptionAllowed;

    public VerificationGateException(List<MissingStep> missingSteps, boolean exceptionAllowed) {
        super("Missing required verification documents");
        this.missingSteps = missingSteps;
        this.exceptionAllowed = exceptionAllowed;
    }

    public List<MissingStep> getMissingSteps() {
        return missingSteps;
    }

    public boolean isExceptionAllowed() {
        return exceptionAllowed;
    }
}
