package com.regulyn.dsar.config;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public OncePerRequestFilter tenantContextFilter() {
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(
          HttpServletRequest request,
          HttpServletResponse response,
          FilterChain filterChain
      ) throws ServletException, IOException {
        try {
          String requestId = request.getHeader("X-Request-Id");
          if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
          }

          String traceId = request.getHeader("X-Trace-Id");
          if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
          }

          MDC.put("requestId", requestId);
          MDC.put("traceId", traceId);

          TenantContext context = new TenantContext();
          context.setRequestId(requestId);
          context.setTraceId(traceId);

          String tenantIdHeader = request.getHeader("X-Tenant-Id");
          if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
            try {
              UUID tenantId = UUID.fromString(tenantIdHeader);
              context.setTenantId(tenantId);
              MDC.put("tenantId", tenantId.toString());
            } catch (IllegalArgumentException ignored) {
              // invalid tenant id
            }
          }

          String userIdHeader = request.getHeader("X-User-Id");
          if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
              UUID userId = UUID.fromString(userIdHeader);
              context.setUserId(userId);
              MDC.put("userId", userId.toString());
            } catch (IllegalArgumentException ignored) {
              // invalid user id
            }
          }

          TenantContextHolder.setContext(context);
          filterChain.doFilter(request, response);
        } finally {
          TenantContextHolder.clear();
          MDC.clear();
        }
      }
    };
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        // Local dev: allow requests from the tenant-admin-portal without getting blocked
        // Backend-level authorization (RBAC) can be turned on later.
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .addFilterBefore(tenantContextFilter(), UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList(
        "http://localhost:3002",
        "http://localhost:3000",
        "http://localhost:3001"
    ));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
