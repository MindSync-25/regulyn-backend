package com.regulyn.ropa.service;

import com.regulyn.ropa.api.dto.CrossBorderTransferFilters;
import com.regulyn.ropa.api.dto.CrossBorderTransferResponse;
import com.regulyn.ropa.model.CrossBorderTransferDataCategoryEntity;
import com.regulyn.ropa.model.CrossBorderTransferEntity;
import com.regulyn.ropa.model.CrossBorderTransferPurposeVersionEntity;
import com.regulyn.ropa.repository.CrossBorderTransferDataCategoryRepository;
import com.regulyn.ropa.repository.CrossBorderTransferPurposeVersionRepository;
import com.regulyn.ropa.repository.CrossBorderTransferRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CrossBorderTransferQueryService {

    private final CrossBorderTransferRepository transferRepository;
    private final CrossBorderTransferDataCategoryRepository dataCategoryRepository;
    private final CrossBorderTransferPurposeVersionRepository purposeVersionRepository;
    private final EntityManager entityManager;

    public CrossBorderTransferQueryService(
            CrossBorderTransferRepository transferRepository,
            CrossBorderTransferDataCategoryRepository dataCategoryRepository,
            CrossBorderTransferPurposeVersionRepository purposeVersionRepository,
            EntityManager entityManager) {
        this.transferRepository = transferRepository;
        this.dataCategoryRepository = dataCategoryRepository;
        this.purposeVersionRepository = purposeVersionRepository;
        this.entityManager = entityManager;
    }

    public CrossBorderTransferResponse getTransfer(UUID tenantId, UUID transferId) {
        return transferRepository.findById(transferId)
            .filter(entity -> tenantId.equals(entity.getTenantId()))
            .map(entity -> buildResponseFromEntities(entity,
                dataCategoryRepository.findByTenantIdAndIdTransferId(tenantId, transferId),
                purposeVersionRepository.findByTenantIdAndIdTransferId(tenantId, transferId)))
            .orElse(null);
    }

    public List<CrossBorderTransferResponse> queryTransfers(UUID tenantId, CrossBorderTransferFilters filters) {
        List<CrossBorderTransferEntity> entities = queryEntities(tenantId, filters);
        if (entities.isEmpty()) {
            return List.of();
        }

        List<UUID> transferIds = entities.stream().map(CrossBorderTransferEntity::getId).toList();
        Map<UUID, List<UUID>> dataCategoryMap = dataCategoryRepository.findByTenantIdAndIdTransferIdIn(tenantId, transferIds)
            .stream()
            .collect(Collectors.groupingBy(
                entity -> entity.getId().getTransferId(),
                Collectors.mapping(entity -> entity.getId().getDataCategoryId(), Collectors.toList())
            ));
        Map<UUID, List<UUID>> purposeMap = purposeVersionRepository.findByTenantIdAndIdTransferIdIn(tenantId, transferIds)
            .stream()
            .collect(Collectors.groupingBy(
                entity -> entity.getId().getTransferId(),
                Collectors.mapping(entity -> entity.getId().getPurposeVersionId(), Collectors.toList())
            ));

        List<CrossBorderTransferResponse> responses = new ArrayList<>();
        for (CrossBorderTransferEntity entity : entities) {
            List<UUID> categories = dataCategoryMap.getOrDefault(entity.getId(), List.of());
            List<UUID> purposes = purposeMap.getOrDefault(entity.getId(), List.of());
            responses.add(buildResponse(entity, categories, purposes));
        }
        return responses;
    }

    public List<CrossBorderTransferEntity> queryEntities(UUID tenantId, CrossBorderTransferFilters filters) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<CrossBorderTransferEntity> query = cb.createQuery(CrossBorderTransferEntity.class);
        Root<CrossBorderTransferEntity> root = query.from(CrossBorderTransferEntity.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("tenantId"), tenantId));

        if (filters != null) {
            if (filters.getVendorId() != null) {
                predicates.add(cb.equal(root.get("vendorId"), filters.getVendorId()));
            }
            if (filters.getSystemId() != null) {
                predicates.add(cb.equal(root.get("systemId"), filters.getSystemId()));
            }
            if (filters.getActivityId() != null) {
                predicates.add(cb.equal(root.get("activityId"), filters.getActivityId()));
            }
            if (filters.getSourceRegion() != null) {
                predicates.add(cb.equal(root.get("sourceRegion"), filters.getSourceRegion()));
            }
            if (filters.getDestinationRegion() != null) {
                predicates.add(cb.equal(root.get("destinationRegion"), filters.getDestinationRegion()));
            }
            if (filters.getActiveOnly() != null && filters.getActiveOnly()) {
                Predicate noEnd = cb.isNull(root.get("endedAt"));
                Predicate futureEnd = cb.greaterThan(root.get("endedAt"), Instant.now());
                predicates.add(cb.or(noEnd, futureEnd));
            }
            if (filters.getDataCategoryId() != null) {
                Subquery<UUID> dcSub = query.subquery(UUID.class);
                Root<CrossBorderTransferDataCategoryEntity> dcRoot = dcSub.from(CrossBorderTransferDataCategoryEntity.class);
                dcSub.select(dcRoot.get("id").get("transferId"));
                dcSub.where(
                    cb.equal(dcRoot.get("tenantId"), tenantId),
                    cb.equal(dcRoot.get("id").get("dataCategoryId"), filters.getDataCategoryId()),
                    cb.equal(dcRoot.get("id").get("transferId"), root.get("id"))
                );
                predicates.add(cb.exists(dcSub));
            }
            if (filters.getPurposeVersionId() != null) {
                Subquery<UUID> pvSub = query.subquery(UUID.class);
                Root<CrossBorderTransferPurposeVersionEntity> pvRoot = pvSub.from(CrossBorderTransferPurposeVersionEntity.class);
                pvSub.select(pvRoot.get("id").get("transferId"));
                pvSub.where(
                    cb.equal(pvRoot.get("tenantId"), tenantId),
                    cb.equal(pvRoot.get("id").get("purposeVersionId"), filters.getPurposeVersionId()),
                    cb.equal(pvRoot.get("id").get("transferId"), root.get("id"))
                );
                predicates.add(cb.exists(pvSub));
            }
        }

        query.where(predicates.toArray(new Predicate[0]));
        query.orderBy(cb.desc(root.get("createdAt")));

        return entityManager.createQuery(query)
            .setMaxResults(500)
            .getResultList();
    }

    private CrossBorderTransferResponse buildResponseFromEntities(CrossBorderTransferEntity entity,
                                                                  List<CrossBorderTransferDataCategoryEntity> dataCategories,
                                                                  List<CrossBorderTransferPurposeVersionEntity> purposeVersions) {
        List<UUID> categoryIds = dataCategories.stream()
            .map(row -> row.getId().getDataCategoryId())
            .sorted()
            .toList();
        List<UUID> purposeIds = purposeVersions.stream()
            .map(row -> row.getId().getPurposeVersionId())
            .sorted()
            .toList();
        return buildResponse(entity, categoryIds, purposeIds);
    }

    private CrossBorderTransferResponse buildResponse(CrossBorderTransferEntity entity,
                                                      List<UUID> categoryIds,
                                                      List<UUID> purposeIds) {
        CrossBorderTransferResponse response = new CrossBorderTransferResponse();
        response.setTransferId(entity.getId());
        response.setSystemId(entity.getSystemId());
        response.setActivityId(entity.getActivityId());
        response.setVendorId(entity.getVendorId());
        response.setSourceRegion(entity.getSourceRegion());
        response.setDestinationRegion(entity.getDestinationRegion());
        response.setTransferMechanism(entity.getTransferMechanism());
        response.setLegalBasis(entity.getLegalBasis());
        response.setFrequency(entity.getFrequency());
        response.setStartedAt(entity.getStartedAt());
        response.setEndedAt(entity.getEndedAt());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setDataCategoryIds(categoryIds);
        response.setPurposeVersionIds(purposeIds);
        return response;
    }
}
