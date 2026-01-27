package io.regulyn.identity.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that validates X-Internal-Auth header for internal service-to-service endpoints.
 * Only applies to /internal/** paths.
 */
@Component
@Order(1)
public class InternalAuthFilter extends OncePerRequestFilter {

    private final String internalAuthToken;

    public InternalAuthFilter(@Value("${internal.auth.token:change-me-in-production}") String internalAuthToken) {
        this.internalAuthToken = internalAuthToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        // Only apply to /internal/** endpoints
        if (path.startsWith("/internal/")) {
            String authHeader = request.getHeader("X-Internal-Auth");
            
            if (authHeader == null || !authHeader.equals(internalAuthToken)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Invalid or missing internal auth token\"}");
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
