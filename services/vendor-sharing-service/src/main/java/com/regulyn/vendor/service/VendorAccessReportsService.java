package com.regulyn.vendor.service;

import com.regulyn.vendor.dto.VendorAccessByAccessTypeDto;
import com.regulyn.vendor.dto.VendorAccessByResultDto;
import com.regulyn.vendor.dto.VendorAccessBySystemDto;
import com.regulyn.vendor.dto.VendorAccessEventViewDto;
import com.regulyn.vendor.dto.VendorAccessEventsPageResponse;
import com.regulyn.vendor.dto.VendorAccessSummaryResponseDto;
import com.regulyn.vendor.model.VendorAccessEventEntity;
import com.regulyn.vendor.repository.VendorAccessEventRepository;
import com.regulyn.vendor.repository.VendorAccessEventSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VendorAccessReportsService {

    private final VendorAccessEventRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public VendorAccessReportsService(VendorAccessEventRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public VendorAccessEventsPageResponse queryEvents(
        UUID tenantId,
        UUID vendorId,
        String subjectRef,
        OffsetDateTime from,
        OffsetDateTime to,
        String systemName,
        String accessType,
        String result,
        String correlationId,
        boolean includeRawRef,
        int page,
        int size,
        Sort sort
    ) {
        Specification<VendorAccessEventEntity> spec = VendorAccessEventSpecifications.tenantScoped(tenantId)
            .and(VendorAccessEventSpecifications.accessedBetween(from, to));

        if (vendorId != null) {
            spec = spec.and(VendorAccessEventSpecifications.vendorIdEquals(vendorId));
        }
        if (subjectRef != null) {
            spec = spec.and(VendorAccessEventSpecifications.subjectRefEquals(subjectRef));
        }
        if (systemName != null) {
            spec = spec.and(VendorAccessEventSpecifications.systemNameEquals(systemName));
        }
        if (accessType != null) {
            spec = spec.and(VendorAccessEventSpecifications.accessTypeEquals(accessType));
        }
        if (result != null) {
            spec = spec.and(VendorAccessEventSpecifications.resultEquals(result));
        }
        if (correlationId != null) {
            spec = spec.and(VendorAccessEventSpecifications.correlationIdEquals(correlationId));
        }

        PageRequest pageRequest = PageRequest.of(page, size, sort);
        Page<VendorAccessEventEntity> results = repository.findAll(spec, pageRequest);

        List<VendorAccessEventViewDto> items = results.getContent().stream()
            .map(entity -> toViewDto(entity, includeRawRef))
            .collect(Collectors.toList());

        return new VendorAccessEventsPageResponse(
            results.getNumber(),
            results.getSize(),
            results.getTotalElements(),
            results.getTotalPages(),
            items
        );
    }

    public VendorAccessSummaryResponseDto summarizeVendorAccess(
        UUID tenantId,
        UUID vendorId,
        OffsetDateTime from,
        OffsetDateTime to,
        String systemName,
        String accessType,
        String result
    ) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("tenant_id = ? AND vendor_id = ? AND accessed_at >= ? AND accessed_at <= ?");
        params.add(tenantId);
        params.add(vendorId);
        params.add(Timestamp.from(from.toInstant()));
        params.add(Timestamp.from(to.toInstant()));

        if (systemName != null) {
            where.append(" AND system_name = ?");
            params.add(systemName);
        }
        if (accessType != null) {
            where.append(" AND access_type = ?");
            params.add(accessType);
        }
        if (result != null) {
            where.append(" AND result = ?");
            params.add(result);
        }

        long total = queryCount("SELECT COUNT(*) FROM vendor.vendor_access_events WHERE " + where, params);
        long allowed = queryCount("SELECT COUNT(*) FROM vendor.vendor_access_events WHERE " + where + " AND result = 'ALLOWED'", params);
        long denied = queryCount("SELECT COUNT(*) FROM vendor.vendor_access_events WHERE " + where + " AND result = 'DENIED'", params);
        long error = queryCount("SELECT COUNT(*) FROM vendor.vendor_access_events WHERE " + where + " AND result = 'ERROR'", params);

        List<VendorAccessBySystemDto> bySystem = jdbcTemplate.query(
            "SELECT system_name, COUNT(*) AS total FROM vendor.vendor_access_events WHERE " + where + " GROUP BY system_name",
            params.toArray(),
            (rs, rowNum) -> new VendorAccessBySystemDto(rs.getString("system_name"), rs.getLong("total"))
        );

        List<VendorAccessByAccessTypeDto> byAccessType = jdbcTemplate.query(
            "SELECT access_type, COUNT(*) AS total FROM vendor.vendor_access_events WHERE " + where + " GROUP BY access_type",
            params.toArray(),
            (rs, rowNum) -> new VendorAccessByAccessTypeDto(rs.getString("access_type"), rs.getLong("total"))
        );

        List<VendorAccessByResultDto> byResult = jdbcTemplate.query(
            "SELECT result, COUNT(*) AS total FROM vendor.vendor_access_events WHERE " + where + " GROUP BY result",
            params.toArray(),
            (rs, rowNum) -> new VendorAccessByResultDto(rs.getString("result"), rs.getLong("total"))
        );

        VendorAccessSummaryResponseDto response = new VendorAccessSummaryResponseDto();
        response.setVendorId(vendorId);
        response.setFrom(from);
        response.setTo(to);
        response.setTotal(total);
        response.setAllowed(allowed);
        response.setDenied(denied);
        response.setError(error);
        response.setBySystem(bySystem);
        response.setByAccessType(byAccessType);
        response.setByResult(byResult);
        return response;
    }

    private long queryCount(String sql, List<Object> params) {
        Long value = jdbcTemplate.queryForObject(sql, params.toArray(), Long.class);
        return value == null ? 0L : value;
    }

    private VendorAccessEventViewDto toViewDto(VendorAccessEventEntity entity, boolean includeRawRef) {
        VendorAccessEventViewDto dto = new VendorAccessEventViewDto();
        dto.setAccessEventId(entity.getAccessEventId());
        dto.setVendorId(entity.getVendorId());
        dto.setSystemName(entity.getSystemName());
        dto.setAccessType(entity.getAccessType());
        dto.setSubjectRef(entity.getSubjectRef());
        dto.setDataCategories(entity.getDataCategories());
        dto.setPurposeRef(entity.getPurposeRef());
        dto.setPurposeVersion(entity.getPurposeVersion());
        dto.setAccessedAt(entity.getAccessedAt());
        dto.setCorrelationId(entity.getCorrelationId());
        dto.setActorType(entity.getActorType());
        dto.setActorId(entity.getActorId());
        dto.setIp(entity.getIp());
        dto.setUserAgent(entity.getUserAgent());
        dto.setResult(entity.getResult());
        dto.setRawPayloadHash(entity.getRawPayloadHash());
        if (includeRawRef) {
            dto.setRawPayloadRef(entity.getRawPayloadRef());
        }
        dto.setReceivedAt(entity.getReceivedAt());
        return dto;
    }
}
