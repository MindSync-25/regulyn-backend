package com.regulyn.notification.config;

import org.springframework.security.config.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Dev enablement:
 * - Allows browser calls from local UIs without needing JWT wiring.
 * - Prevents CORS preflight (OPTIONS) from being blocked.
 *
 * IMPORTANT: Only active for the "dev" Spring profile.
 */
@Configuration
@Profile("dev")
public class DevSecurityConfig {

  @Bean
  @Order(0)
  public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        // Use the shared CORS bean from lib-auth (already allows localhost:3002)
        .cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .anyRequest().permitAll()
        );

    return http.build();
  }
}
