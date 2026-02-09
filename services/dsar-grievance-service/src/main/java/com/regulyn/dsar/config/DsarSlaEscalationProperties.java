package com.regulyn.dsar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "dsar.sla.escalation")
public class DsarSlaEscalationProperties {

    private boolean enabled = true;
    private String cron = "0 */15 * * * *";
    private Thresholds thresholds = new Thresholds();
    private Recipients recipients = new Recipients();
    private Notification notification = new Notification();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public Thresholds getThresholds() {
        return thresholds;
    }

    public void setThresholds(Thresholds thresholds) {
        this.thresholds = thresholds;
    }

    public Recipients getRecipients() {
        return recipients;
    }

    public void setRecipients(Recipients recipients) {
        this.recipients = recipients;
    }

    public Notification getNotification() {
        return notification;
    }

    public void setNotification(Notification notification) {
        this.notification = notification;
    }

    public static class Thresholds {
        private Integer warningDays = 60;
        private Integer criticalDays = 80;
        private Integer breachDays = 90;

        public Integer getWarningDays() {
            return warningDays;
        }

        public void setWarningDays(Integer warningDays) {
            this.warningDays = warningDays;
        }

        public Integer getCriticalDays() {
            return criticalDays;
        }

        public void setCriticalDays(Integer criticalDays) {
            this.criticalDays = criticalDays;
        }

        public Integer getBreachDays() {
            return breachDays;
        }

        public void setBreachDays(Integer breachDays) {
            this.breachDays = breachDays;
        }
    }

    public static class Recipients {
        private List<String> emails = new ArrayList<>();

        public List<String> getEmails() {
            return emails;
        }

        public void setEmails(List<String> emails) {
            this.emails = emails;
        }
    }

    public static class Notification {
        private String templateKey = "DSAR_SLA_ESCALATION";
        private String fromName = "Regulyn";
        private String category = "LEGAL";
        private String language = "en";

        public String getTemplateKey() {
            return templateKey;
        }

        public void setTemplateKey(String templateKey) {
            this.templateKey = templateKey;
        }

        public String getFromName() {
            return fromName;
        }

        public void setFromName(String fromName) {
            this.fromName = fromName;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }
    }
}
