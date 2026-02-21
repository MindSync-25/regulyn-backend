package io.regulyn.identity.service;

import io.regulyn.identity.dto.PlatformAuditEventDTO;
import io.regulyn.identity.dto.PlatformAuditPageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PlatformAuditService {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public PlatformAuditService(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public PlatformAuditPageResponse query(String tenantId,
                                           String actorId,
                                           String eventType,
                                           String correlationId,
                                           String from,
                                           String to,
                                           int page,
                                           int size) {
        if (page < 0 || size <= 0 || size > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAGINATION_INVALID");
        }

        UUID tenantUuid = parseUuid(tenantId, "TENANT_ID_INVALID");
        UUID actorUuid = parseUuid(actorId, "ACTOR_ID_INVALID");
        Instant fromInstant = parseInstant(from, "FROM_INVALID");
        Instant toInstant = parseInstant(to, "TO_INVALID");

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("limit", size);
        params.addValue("offset", page * size);

        StringBuilder whereClause = new StringBuilder("where 1=1 ");
        if (tenantUuid != null) {
            whereClause.append("and tenant_id = :tenantId ");
            params.addValue("tenantId", tenantUuid);
        }
        if (actorUuid != null) {
            whereClause.append("and actor_id = :actorId ");
            params.addValue("actorId", actorUuid);
        }
        if (eventType != null && !eventType.isBlank()) {
            whereClause.append("and action = :eventType ");
            params.addValue("eventType", eventType);
        }
        if (correlationId != null && !correlationId.isBlank()) {
            whereClause.append("and metadata->>'correlationId' = :correlationId ");
            params.addValue("correlationId", correlationId);
        }
        if (fromInstant != null) {
            whereClause.append("and occurred_at >= :from ");
            params.addValue("from", Timestamp.from(fromInstant));
        }
        if (toInstant != null) {
            whereClause.append("and occurred_at <= :to ");
            params.addValue("to", Timestamp.from(toInstant));
        }

        String countSql = "select count(*) from identity.audit_events " + whereClause;
        Long total = namedParameterJdbcTemplate.queryForObject(countSql, params, Long.class);

        String querySql = "select event_id, tenant_id, occurred_at, actor_id, action, "
            + "metadata->>'correlationId' as correlation_id, metadata->>'summary' as summary "
            + "from identity.audit_events "
            + whereClause + " order by occurred_at desc limit :limit offset :offset";

        List<PlatformAuditEventDTO> items = namedParameterJdbcTemplate.query(querySql, params, (rs, rowNum) -> {
            PlatformAuditEventDTO dto = new PlatformAuditEventDTO();
            dto.setId(rs.getObject("event_id", UUID.class));
            dto.setTenantId(rs.getObject("tenant_id", UUID.class));
            dto.setOccurredAt(rs.getTimestamp("occurred_at").toInstant());
            dto.setActorId(rs.getObject("actor_id", UUID.class));
            dto.setEventType(rs.getString("action"));
            dto.setCorrelationId(rs.getString("correlation_id"));
            String summary = rs.getString("summary");
            dto.setSummary(summary != null && summary.length() <= 160 ? summary : null);
            return dto;
        });

        PlatformAuditPageResponse response = new PlatformAuditPageResponse();
        response.setContent(items);
        long totalElements = total != null ? total : 0L;
        response.setTotalElements(totalElements);
        response.setSize(size);
        response.setNumber(page);
        response.setTotalPages(size == 0 ? 0 : (int) Math.ceil((double) totalElements / size));
        return response;
    }

    private UUID parseUuid(String value, String errorCode) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, errorCode);
        }
    }

    private Instant parseInstant(String value, String errorCode) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, errorCode);
        }
    }
}
