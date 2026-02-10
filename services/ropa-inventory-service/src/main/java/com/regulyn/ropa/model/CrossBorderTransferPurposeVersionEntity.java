package com.regulyn.ropa.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "cross_border_transfer_purpose_versions", schema = "ropa")
public class CrossBorderTransferPurposeVersionEntity {

    @EmbeddedId
    private CrossBorderTransferPurposeVersionId id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    public CrossBorderTransferPurposeVersionId getId() {
        return id;
    }

    public void setId(CrossBorderTransferPurposeVersionId id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }
}
