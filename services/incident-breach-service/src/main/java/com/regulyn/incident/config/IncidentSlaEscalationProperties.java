package com.regulyn.incident.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "incident.sla-escalation")
public class IncidentSlaEscalationProperties {

    private List<String> dpoEmails = List.of();
    private List<String> adminEmails = List.of();

    public List<String> getDpoEmails() {
        return dpoEmails;
    }

    public void setDpoEmails(List<String> dpoEmails) {
        this.dpoEmails = dpoEmails;
    }

    public List<String> getAdminEmails() {
        return adminEmails;
    }

    public void setAdminEmails(List<String> adminEmails) {
        this.adminEmails = adminEmails;
    }
}
