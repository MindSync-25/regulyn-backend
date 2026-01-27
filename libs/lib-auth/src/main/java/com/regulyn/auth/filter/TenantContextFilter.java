package com.regulyn.auth.filter;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.auth.jwt.JwtTokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class TenantContextFilter extends OncePerRequestFilter {
  
  private static final Logger logger = LoggerFactory.getLogger(TenantContextFilter.class);
  private final JwtTokenService jwtTokenService;
  
  public TenantContextFilter(JwtTokenService jwtTokenService) {
    this.jwtTokenService = jwtTokenService;
  }
  
  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      // Generate request ID
      String requestId = request.getHeader("X-Request-Id");
      if (requestId == null || requestId.isBlank()) {
        requestId = UUID.randomUUID().toString();
      }
      
      // Generate trace ID
      String traceId = request.getHeader("X-Trace-Id");
      if (traceId == null || traceId.isBlank()) {
        traceId = UUID.randomUUID().toString();
      }
      
      // Add to MDC
      MDC.put("requestId", requestId);
      MDC.put("traceId", traceId);
      
      TenantContext context = new TenantContext();
      context.setRequestId(requestId);
      context.setTraceId(traceId);
      
      // Try to extract from JWT first
      String authHeader = request.getHeader("Authorization");
      if (authHeader != null && authHeader.startsWith("Bearer ")) {
        String token = authHeader.substring(7);
        try {
          Claims claims = jwtTokenService.validateAndExtractClaims(token);
          UUID tenantId = jwtTokenService.getTenantId(claims);
          UUID userId = jwtTokenService.getUserId(claims);
          List<String> rolesList = jwtTokenService.getRoles(claims);
          
          context.setTenantId(tenantId);
          context.setUserId(userId);
          context.setRoles(new HashSet<>(rolesList));
          
          if (tenantId != null) {
            MDC.put("tenantId", tenantId.toString());
          }
          if (userId != null) {
            MDC.put("userId", userId.toString());
          }
        } catch (Exception e) {
          logger.warn("Failed to parse JWT token: {}", e.getMessage());
        }
      }
      
      // Fallback to headers if JWT not present
      if (context.getTenantId() == null) {
        String tenantIdHeader = request.getHeader("X-Tenant-Id");
        if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
          try {
            UUID tenantId = UUID.fromString(tenantIdHeader);
            context.setTenantId(tenantId);
            MDC.put("tenantId", tenantId.toString());
          } catch (IllegalArgumentException e) {
            logger.warn("Invalid X-Tenant-Id header: {}", tenantIdHeader);
          }
        }
      }
      
      // Optional actor ID
      String actorHeader = request.getHeader("X-Actor-Id");
      if (actorHeader != null && !actorHeader.isBlank() && context.getUserId() == null) {
        try {
          context.setUserId(UUID.fromString(actorHeader));
        } catch (IllegalArgumentException e) {
          logger.warn("Invalid X-Actor-Id header: {}", actorHeader);
        }
      }
      
      TenantContextHolder.setContext(context);
      
      filterChain.doFilter(request, response);
    } finally {
      TenantContextHolder.clear();
      MDC.clear();
    }
  }
}
