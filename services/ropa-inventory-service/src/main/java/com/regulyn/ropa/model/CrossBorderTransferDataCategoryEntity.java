package com.regulyn.ropa.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "cross_border_transfer_data_categories", schema = "ropa")
public class CrossBorderTransferDataCategoryEntity {

    @EmbeddedId
    private CrossBorderTransferDataCategoryId id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    public CrossBorderTransferDataCategoryId getId() {
        return id;
    }

    public void setId(CrossBorderTransferDataCategoryId id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }
}
