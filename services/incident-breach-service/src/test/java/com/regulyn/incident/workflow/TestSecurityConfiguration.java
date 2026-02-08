package com.regulyn.incident.workflow;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@TestConfiguration
public class TestSecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public FilterRegistrationBean<TestTenantFilter> testTenantFilter() {
        FilterRegistrationBean<TestTenantFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new TestTenantFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }

    static class TestTenantFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            try {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                String tenantIdHeader = httpRequest.getHeader("X-Tenant-ID");
                String actorIdHeader = httpRequest.getHeader("X-Actor-ID");
                String actorRoleHeader = httpRequest.getHeader("X-Actor-Role");

                TenantContext context = new TenantContext();
                context.setRequestId(UUID.randomUUID().toString());
                context.setTraceId(UUID.randomUUID().toString());

                if (tenantIdHeader != null && !tenantIdHeader.isBlank()) {
                    context.setTenantId(UUID.fromString(tenantIdHeader));
                }
                if (actorIdHeader != null && !actorIdHeader.isBlank()) {
                    context.setUserId(UUID.fromString(actorIdHeader));
                }
                if (actorRoleHeader != null && !actorRoleHeader.isBlank()) {
                    context.setRoles(Set.of(actorRoleHeader));
                }

                TenantContextHolder.setContext(context);
                chain.doFilter(request, response);
            } finally {
                TenantContextHolder.clear();
            }
        }
    }
}
