package com.regulyn.auth.config;

import com.regulyn.auth.filter.TenantContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
  
  private final TenantContextFilter tenantContextFilter;
  private final Environment environment;
  
  public SecurityConfig(TenantContextFilter tenantContextFilter, Environment environment) {
    this.tenantContextFilter = tenantContextFilter;
    this.environment = environment;
  }
  
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> {
          auth.requestMatchers("/actuator/health", "/actuator/info").permitAll();
          auth.requestMatchers("/actuator/**").permitAll();
          auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll();
          auth.requestMatchers("/users/invites/accept").permitAll();
          
          // In local profile, permit /auth/login
          if (isLocalProfile()) {
            auth.requestMatchers("/auth/login").permitAll();
          }
          
          auth.anyRequest().authenticated();
        })
        .addFilterBefore(tenantContextFilter, UsernamePasswordAuthenticationFilter.class);
    
    return http.build();
  }
  
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
  
  private boolean isLocalProfile() {
    String[] activeProfiles = environment.getActiveProfiles();
    for (String profile : activeProfiles) {
      if ("local".equals(profile)) {
        return true;
      }
    }
    return false;
  }
}
