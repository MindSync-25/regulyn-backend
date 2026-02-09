package com.regulyn.vendor.repository;

import com.regulyn.vendor.model.VendorAccessEventEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class VendorAccessEventSpecifications {

    private VendorAccessEventSpecifications() {
    }

    public static Specification<VendorAccessEventEntity> tenantScoped(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<VendorAccessEventEntity> accessedBetween(OffsetDateTime from, OffsetDateTime to) {
        return (root, query, cb) -> cb.between(root.get("accessedAt"), from, to);
    }

    public static Specification<VendorAccessEventEntity> vendorIdEquals(UUID vendorId) {
        return (root, query, cb) -> cb.equal(root.get("vendorId"), vendorId);
    }

    public static Specification<VendorAccessEventEntity> subjectRefEquals(String subjectRef) {
        return (root, query, cb) -> cb.equal(root.get("subjectRef"), subjectRef);
    }

    public static Specification<VendorAccessEventEntity> systemNameEquals(String systemName) {
        return (root, query, cb) -> cb.equal(root.get("systemName"), systemName);
    }

    public static Specification<VendorAccessEventEntity> accessTypeEquals(String accessType) {
        return (root, query, cb) -> cb.equal(root.get("accessType"), accessType);
    }

    public static Specification<VendorAccessEventEntity> resultEquals(String result) {
        return (root, query, cb) -> cb.equal(root.get("result"), result);
    }

    public static Specification<VendorAccessEventEntity> correlationIdEquals(String correlationId) {
        return (root, query, cb) -> cb.equal(root.get("correlationId"), correlationId);
    }
}
