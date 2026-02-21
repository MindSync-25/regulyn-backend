package com.regulyn.auth.config;

import com.regulyn.auth.filter.JwtAuthenticationFilter;
import com.regulyn.auth.filter.TenantContextFilter;
import com.regulyn.auth.jwt.JwtTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
  
  private final TenantContextFilter tenantContextFilter;
  private final JwtTokenService jwtTokenService;
  private final Environment environment;
  
  public SecurityConfig(TenantContextFilter tenantContextFilter,
                        JwtTokenService jwtTokenService,
                        Environment environment) {
    this.tenantContextFilter = tenantContextFilter;
    this.jwtTokenService = jwtTokenService;
    this.environment = environment;
  }
  
  @Bean
  public JwtAuthenticationFilter jwtAuthenticationFilter() {
    return new JwtAuthenticationFilter(jwtTokenService);
  }
  
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
    http
        .cors(Customizer.withDefaults()) // Enable CORS using WebConfig
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> {
          // Permit all OPTIONS requests for CORS preflight
          auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
          
          auth.requestMatchers("/actuator/health", "/actuator/info").permitAll();
          auth.requestMatchers("/actuator/**").permitAll();
          auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll();
          auth.requestMatchers("/users/invites/accept").permitAll();
          
          // In local profile, permit /auth/login and tenant signup
          if (isLocalProfile()) {
            auth.requestMatchers("/auth/login").permitAll();
            auth.requestMatchers("/auth/me").permitAll();
            auth.requestMatchers(HttpMethod.POST, "/tenants").permitAll();
            auth.requestMatchers(HttpMethod.POST, "/tenants/*/bootstrap-admin").permitAll();
          }
          
          auth.anyRequest().authenticated();
        })
        .addFilterBefore(jwtFilter, AnonymousAuthenticationFilter.class)
        .addFilterAfter(tenantContextFilter, JwtAuthenticationFilter.class);
    
    return http.build();
  }
  
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList(
      "http://localhost:3000",
      "http://localhost:3001",
      "http://localhost:3002"
    ));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);
    
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
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
