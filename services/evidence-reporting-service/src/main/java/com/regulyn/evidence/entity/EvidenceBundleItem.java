package com.regulyn.evidence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "evidence_bundle_items", schema = "evidence")
public class EvidenceBundleItem {

    @Id
    @Column(name = "item_id")
    private UUID itemId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "bundle_id", nullable = false)
    private UUID bundleId;

    @Column(name = "item_type", nullable = false, length = 20)
    private String itemType;

    @Column(name = "evidence_id")
    private UUID evidenceId;

    @Column(name = "artifact_id")
    private UUID artifactId;

    @Column(name = "item_hash", nullable = false, length = 64)
    private String itemHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "item_meta", columnDefinition = "jsonb")
    private Map<String, Object> itemMeta;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (itemId == null) {
            itemId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Getters and Setters
    public UUID getItemId() {
        return itemId;
    }

    public void setItemId(UUID itemId) {
        this.itemId = itemId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public void setBundleId(UUID bundleId) {
        this.bundleId = bundleId;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public UUID getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(UUID evidenceId) {
        this.evidenceId = evidenceId;
    }

    public UUID getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(UUID artifactId) {
        this.artifactId = artifactId;
    }

    public String getItemHash() {
        return itemHash;
    }

    public void setItemHash(String itemHash) {
        this.itemHash = itemHash;
    }

    public Map<String, Object> getItemMeta() {
        return itemMeta;
    }

    public void setItemMeta(Map<String, Object> itemMeta) {
        this.itemMeta = itemMeta;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
