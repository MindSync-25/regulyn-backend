package com.regulyn.incident.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "incident.close")
public class IncidentCloseProperties {

    private boolean requireEvidenceBundle = true;

    public boolean isRequireEvidenceBundle() {
        return requireEvidenceBundle;
    }

    public void setRequireEvidenceBundle(boolean requireEvidenceBundle) {
        this.requireEvidenceBundle = requireEvidenceBundle;
    }
}
