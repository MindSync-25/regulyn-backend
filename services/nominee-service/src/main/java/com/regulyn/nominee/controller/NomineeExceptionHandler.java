package com.regulyn.nominee.controller;

import com.regulyn.nominee.dto.VerificationGateResponse;
import com.regulyn.nominee.exception.VerificationGateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class NomineeExceptionHandler {

    @ExceptionHandler(VerificationGateException.class)
    public ResponseEntity<VerificationGateResponse> handleVerificationGate(VerificationGateException ex) {
        VerificationGateResponse response = new VerificationGateResponse();
        response.setError("MISSING_REQUIRED_DOCS");
        response.setMissingSteps(ex.getMissingSteps());
        response.setExceptionAllowed(ex.isExceptionAllowed());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
}
