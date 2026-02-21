package com.regulyn.auth.filter;

import com.regulyn.auth.jwt.JwtTokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            try {
                Claims claims = jwtTokenService.validateAndExtractClaims(token);
                String userId = jwtTokenService.getUserId(claims).toString();
                List<String> roles = jwtTokenService.getRoles(claims);
                
                // Convert roles to Spring Security authorities
                // Important: Spring Security's @PreAuthorize("hasRole('X')") expects authorities with "ROLE_" prefix
                // But since our roles already have descriptive names like REGULYN_SUPER_ADMIN, we add them as-is
                // and also with ROLE_ prefix for compatibility
                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .flatMap(role -> List.of(
                                new SimpleGrantedAuthority(role),
                                new SimpleGrantedAuthority("ROLE_" + role)
                        ).stream())
                        .collect(Collectors.toList());
                
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // JWT validation failed - let Spring Security handle it
                logger.debug("JWT authentication failed: {}", e.getMessage());
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
