package com.regulyn.common.exception;

import com.regulyn.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {
  
  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException ex, HttpServletRequest request) {
    logger.warn("Conflict: {}", ex.getMessage());
    
    ErrorResponse error = new ErrorResponse(
      HttpStatus.CONFLICT.value(),
      "Conflict",
      ex.getMessage(),
      request.getRequestURI(),
      MDC.get("requestId"),
      MDC.get("tenantId")
    );
    
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
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
  
  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
    // WORKAROUND: Spring's @ExceptionHandler resolution doesn't always pick the most specific handler.
    // Explicitly delegate to ensure IllegalStateException → 409 and IllegalArgumentException → 400.
    // This is fragile - prefer throwing custom exceptions or using @ResponseStatus where possible.
    if (ex instanceof IllegalStateException) {
      return handleIllegalStateException((IllegalStateException) ex, request);
    }
    if (ex instanceof IllegalArgumentException) {
      return handleIllegalArgumentException((IllegalArgumentException) ex, request);
    }
    
    // Check if it's an EvidenceServiceUnavailableException by class name
    if (ex.getClass().getSimpleName().equals("EvidenceServiceUnavailableException")) {
      logger.error("Evidence service unavailable: {}", ex.getMessage());
      
      ErrorResponse error = new ErrorResponse(
        HttpStatus.SERVICE_UNAVAILABLE.value(),
        "Service Unavailable",
        ex.getMessage() != null ? ex.getMessage() : "Evidence service unavailable",
        request.getRequestURI(),
        MDC.get("requestId"),
        MDC.get("tenantId")
      );
      
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }
    
    // For other runtime exceptions, return 500
    logger.error("Unhandled runtime exception", ex);
    
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
