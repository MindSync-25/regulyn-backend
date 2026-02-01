package com.regulyn.guardian.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Order(1)
    public TenantContextFilter testTenantContextFilter() {
        return new TenantContextFilter();
    }

    public static class TenantContextFilter extends OncePerRequestFilter {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {

            String contextHeader = request.getHeader("X-Tenant-Context");

            if (contextHeader != null && !contextHeader.isEmpty()) {
                try {
                    Map<String, Object> contextMap = objectMapper.readValue(contextHeader, Map.class);

                    UUID tenantId = UUID.fromString((String) contextMap.get("tenantId"));
                    UUID userId = UUID.fromString((String) contextMap.get("userId"));

                    @SuppressWarnings("unchecked")
                    Set<String> roles = ((java.util.List<String>) contextMap.get("roles"))
                        .stream()
                        .collect(Collectors.toSet());

                    TenantContext context = new TenantContext(
                        tenantId,
                        userId,
                        roles,
                        null,
                        UUID.randomUUID().toString()
                    );

                    TenantContextHolder.setContext(context);

                } catch (Exception e) {
                    // Invalid context header, continue without context
                }
            }

            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContextHolder.clear();
            }
        }
    }
}
