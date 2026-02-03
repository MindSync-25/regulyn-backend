package com.regulyn.notification.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
    name = "notification_requests",
    schema = "notification",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_notification_request_ref", columnNames = {"tenant_id", "request_ref"})
    }
)
public class NotificationRequest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "request_id")
    private UUID requestId;
    
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;
    
    @Column(name = "request_ref", length = 200)
    private String requestRef;
    
    @Column(name = "template_id", nullable = false)
    private UUID templateId;
    
    @Column(name = "version_id", nullable = false)
    private UUID versionId;
    
    @Column(name = "language", nullable = false, length = 10)
    private String language;
    
    @Column(name = "channel", nullable = false, length = 50)
    private String channel;
    
    @Column(name = "audience_type", nullable = false, length = 50)
    private String audienceType;
    
    @Column(name = "audience_data_principal_id", length = 100)
    private String audienceDataPrincipalId;
    
    @Column(name = "audience_user_ids", columnDefinition = "TEXT")
    private String audienceUserIds;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables", columnDefinition = "jsonb")
    private Map<String, String> variables;
    
    @Column(name = "total_recipients", nullable = false)
    private Integer totalRecipients;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "message_category", nullable = false, length = 50)
    private MessageCategory messageCategory = MessageCategory.MARKETING;
    
    @Column(name = "sent_count", nullable = false)
    private Integer sentCount = 0;
    
    @Column(name = "skipped_count", nullable = false)
    private Integer skippedCount = 0;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
    
    // Getters and Setters
    public UUID getRequestId() {
        return requestId;
    }
    
    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    
    public String getRequestRef() {
        return requestRef;
    }
    
    public void setRequestRef(String requestRef) {
        this.requestRef = requestRef;
    }
    
    public UUID getTemplateId() {
        return templateId;
    }
    
    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }
    
    public UUID getVersionId() {
        return versionId;
    }
    
    public void setVersionId(UUID versionId) {
        this.versionId = versionId;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public String getChannel() {
        return channel;
    }
    
    public void setChannel(String channel) {
        this.channel = channel;
    }
    
    public String getAudienceType() {
        return audienceType;
    }
    
    public void setAudienceType(String audienceType) {
        this.audienceType = audienceType;
    }
    
    public String getAudienceDataPrincipalId() {
        return audienceDataPrincipalId;
    }
    
    public void setAudienceDataPrincipalId(String audienceDataPrincipalId) {
        this.audienceDataPrincipalId = audienceDataPrincipalId;
    }
    
    public String getAudienceUserIds() {
        return audienceUserIds;
    }
    
    public void setAudienceUserIds(String audienceUserIds) {
        this.audienceUserIds = audienceUserIds;
    }
    
    public Map<String, String> getVariables() {
        return variables;
    }
    
    public void setVariables(Map<String, String> variables) {
        this.variables = variables;
    }
    
    public Integer getTotalRecipients() {
        return totalRecipients;
    }
    
    public void setTotalRecipients(Integer totalRecipients) {
        this.totalRecipients = totalRecipients;
    }
    
    public MessageCategory getMessageCategory() {
        return messageCategory;
    }
    
    public void setMessageCategory(MessageCategory messageCategory) {
        this.messageCategory = messageCategory;
    }
    
    public Integer getSentCount() {
        return sentCount;
    }
    
    public void setSentCount(Integer sentCount) {
        this.sentCount = sentCount;
    }
    
    public Integer getSkippedCount() {
        return skippedCount;
    }
    
    public void setSkippedCount(Integer skippedCount) {
        this.skippedCount = skippedCount;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
