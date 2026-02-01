package io.regulyn.scanner.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "scan_promotions", schema = "scanner")
public class ScanPromotion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "promotion_id")
    private UUID promotionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "promoted_at", nullable = false, updatable = false)
    private Instant promotedAt;

    @Column(name = "promoted_count", nullable = false)
    private Integer promotedCount;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount;

    @Type(JsonBinaryType.class)
    @Column(name = "details", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> details = new HashMap<>();

    @PrePersist
    protected void onCreate() {
        promotedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getPromotionId() {
        return promotionId;
    }

    public void setPromotionId(UUID promotionId) {
        this.promotionId = promotionId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public Instant getPromotedAt() {
        return promotedAt;
    }

    public void setPromotedAt(Instant promotedAt) {
        this.promotedAt = promotedAt;
    }

    public Integer getPromotedCount() {
        return promotedCount;
    }

    public void setPromotedCount(Integer promotedCount) {
        this.promotedCount = promotedCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
}
