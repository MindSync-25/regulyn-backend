package com.regulyn.notification.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@EnableConfigurationProperties(SmtpProperties.class)
public class SmtpConfig {
    
    @Bean
    @ConditionalOnProperty(prefix = "smtp", name = "enabled", havingValue = "true")
    public JavaMailSender javaMailSender(SmtpProperties properties) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(properties.getHost());
        mailSender.setPort(properties.getPort());
        mailSender.setUsername(properties.getUsername());
        mailSender.setPassword(properties.getPassword());
        
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(properties.isAuth()));
        props.put("mail.smtp.starttls.enable", String.valueOf(properties.isStarttls()));
        props.put("mail.smtp.starttls.required", String.valueOf(properties.isStarttls()));
        
        return mailSender;
    }
}