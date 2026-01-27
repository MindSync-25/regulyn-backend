package com.regulyn.auth.apikey;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.auth.model.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

@Component
@Order(1)
public class ApiKeyAuthFilter extends OncePerRequestFilter {
  
  private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
  private final ApiKeyValidator apiKeyValidator;
  
  public ApiKeyAuthFilter(ApiKeyValidator apiKeyValidator) {
    this.apiKeyValidator = apiKeyValidator;
  }
  
  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    
    String apiKey = request.getHeader("X-API-Key");
    
    if (apiKey != null && !apiKey.isBlank()) {
      try {
        String apiKeyHash = hashApiKey(apiKey);
        ApiKeyValidator.ApiKeyValidationResult result = apiKeyValidator.validate(apiKeyHash);
        
        if (result.isValid()) {
          TenantContext context = TenantContextHolder.getContext();
          context.setTenantId(result.getTenantId());
          context.setRoles(Set.of(Role.CONNECTOR_AGENT.name()));
          
          MDC.put("tenantId", result.getTenantId().toString());
          MDC.put("apiKeyName", result.getName());
          
          logger.debug("API key authenticated: {}", result.getName());
        } else {
          logger.warn("Invalid API key attempted");
        }
      } catch (Exception e) {
        logger.error("Error validating API key", e);
      }
    }
    
    filterChain.doFilter(request, response);
  }
  
  private String hashApiKey(String apiKey) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) hexString.append('0');
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not found", e);
    }
  }
}
