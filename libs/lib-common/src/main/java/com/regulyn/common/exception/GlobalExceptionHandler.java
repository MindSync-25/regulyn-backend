package com.regulyn.common.exception;

import com.regulyn.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  
  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
    logger.error("Unhandled exception", ex);
    
    ErrorResponse error = new ErrorResponse(
      HttpStatus.INTERNAL_SERVER_ERROR.value(),
      "Internal Server Error",
      ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred",
      request.getRequestURI(),
      MDC.get("requestId"),
      MDC.get("tenantId")
    );
    
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }
  
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
    logger.warn("Bad request: {}", ex.getMessage());
    
    ErrorResponse error = new ErrorResponse(
      HttpStatus.BAD_REQUEST.value(),
      "Bad Request",
      ex.getMessage(),
      request.getRequestURI(),
      MDC.get("requestId"),
      MDC.get("tenantId")
    );
    
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }
  
  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFoundException(NoHandlerFoundException ex, HttpServletRequest request) {
    ErrorResponse error = new ErrorResponse(
      HttpStatus.NOT_FOUND.value(),
      "Not Found",
      "Resource not found",
      request.getRequestURI(),
      MDC.get("requestId"),
      MDC.get("tenantId")
    );
    
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }
}
