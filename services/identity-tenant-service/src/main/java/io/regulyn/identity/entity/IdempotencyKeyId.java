package io.regulyn.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class IdempotencyKeyId implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "scope", nullable = false, length = 80)
    private String scope;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    public IdempotencyKeyId() {
    }

    public IdempotencyKeyId(UUID tenantId, String scope, String idempotencyKey) {
        this.tenantId = tenantId;
        this.scope = scope;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IdempotencyKeyId that = (IdempotencyKeyId) o;
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(scope, that.scope)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, scope, idempotencyKey);
    }
}
