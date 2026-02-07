package com.regulyn.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smtp")
public class SmtpProperties {
    private boolean enabled = false;
    private String host;
    private Integer port = 587;
    private String username;
    private String password;
    private boolean starttls = true;
    private boolean auth = true;
    private String from;
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
    
    public Integer getPort() {
        return port;
    }
    
    public void setPort(Integer port) {
        this.port = port;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public boolean isStarttls() {
        return starttls;
    }
    
    public void setStarttls(boolean starttls) {
        this.starttls = starttls;
    }
    
    public boolean isAuth() {
        return auth;
    }
    
    public void setAuth(boolean auth) {
        this.auth = auth;
    }
    
    public String getFrom() {
        return from;
    }
    
    public void setFrom(String from) {
        this.from = from;
    }
}