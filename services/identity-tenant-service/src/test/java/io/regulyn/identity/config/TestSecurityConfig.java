package io.regulyn.identity.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@TestConfiguration
@Profile("test")
public class TestSecurityConfig {

    @Bean
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(testAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    OncePerRequestFilter testAuthenticationFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                            jakarta.servlet.http.HttpServletResponse response,
                                            jakarta.servlet.FilterChain filterChain)
                    throws jakarta.servlet.ServletException, IOException {
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"));
                    var auth = new UsernamePasswordAuthenticationToken("test-admin", "N/A", authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
                filterChain.doFilter(request, response);
            }
        };
    }
}
