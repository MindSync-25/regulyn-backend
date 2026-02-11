package com.regulyn.nominee.dto;

import java.util.List;

public class VerificationGateResponse {

    private String error;
    private List<MissingStep> missingSteps;
    private boolean exceptionAllowed;

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public List<MissingStep> getMissingSteps() {
        return missingSteps;
    }

    public void setMissingSteps(List<MissingStep> missingSteps) {
        this.missingSteps = missingSteps;
    }

    public boolean isExceptionAllowed() {
        return exceptionAllowed;
    }

    public void setExceptionAllowed(boolean exceptionAllowed) {
        this.exceptionAllowed = exceptionAllowed;
    }
}
