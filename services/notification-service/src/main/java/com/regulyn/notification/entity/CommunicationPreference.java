package com.regulyn.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "communication_preferences",
    schema = "notification",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_communication_preferences", columnNames = {"tenant_id", "data_principal_id", "channel", "category"})
    }
)
public class CommunicationPreference {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "preference_id")
    private UUID preferenceId;
    
    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;
    
    @Column(name = "data_principal_id", nullable = false, length = 100)
    private String dataPrincipalId;
    
    @Column(name = "channel", nullable = false, length = 50)
    private String channel;
    
    @Column(name = "category", nullable = false, length = 50)
    private String category;
    
    @Column(name = "opted_out", nullable = false)
    private Boolean optedOut = false;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    // Getters and Setters
    public UUID getPreferenceId() {
        return preferenceId;
    }
    
    public void setPreferenceId(UUID preferenceId) {
        this.preferenceId = preferenceId;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    
    public String getDataPrincipalId() {
        return dataPrincipalId;
    }
    
    public void setDataPrincipalId(String dataPrincipalId) {
        this.dataPrincipalId = dataPrincipalId;
    }
    
    public String getChannel() {
        return channel;
    }
    
    public void setChannel(String channel) {
        this.channel = channel;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public Boolean getOptedOut() {
        return optedOut;
    }
    
    public void setOptedOut(Boolean optedOut) {
        this.optedOut = optedOut;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public String getUpdatedBy() {
        return updatedBy;
    }
    
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
