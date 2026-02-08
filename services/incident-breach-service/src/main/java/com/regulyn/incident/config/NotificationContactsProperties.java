package com.regulyn.incident.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "incident.notifications")
public class NotificationContactsProperties {

    private List<String> authorityContacts = List.of();
    private List<String> boardContacts = List.of();
    private String receiptWebhookSecret = "";

    public List<String> getAuthorityContacts() {
        return authorityContacts;
    }

    public void setAuthorityContacts(List<String> authorityContacts) {
        this.authorityContacts = authorityContacts;
    }

    public List<String> getBoardContacts() {
        return boardContacts;
    }

    public void setBoardContacts(List<String> boardContacts) {
        this.boardContacts = boardContacts;
    }

    public String getReceiptWebhookSecret() {
        return receiptWebhookSecret;
    }

    public void setReceiptWebhookSecret(String receiptWebhookSecret) {
        this.receiptWebhookSecret = receiptWebhookSecret;
    }
}
