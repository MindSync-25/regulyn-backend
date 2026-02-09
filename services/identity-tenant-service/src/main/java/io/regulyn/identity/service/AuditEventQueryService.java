package io.regulyn.identity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.regulyn.identity.dto.AuditEventPageResponse;
import io.regulyn.identity.dto.AuditEventView;
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
public class AuditEventQueryService {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditEventQueryService(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                  ObjectMapper objectMapper) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public AuditEventPageResponse query(UUID tenantId,
                                        UUID actorId,
                                        String eventType,
                                        Instant from,
                                        Instant to,
                                        int page,
                                        int size) {
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_CONTEXT_REQUIRED");
        }
        if (page < 0 || size <= 0 || size > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAGINATION_INVALID");
        }

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("tenantId", tenantId);
        params.addValue("limit", size);
        params.addValue("offset", page * size);

        StringBuilder whereClause = new StringBuilder("where tenant_id = :tenantId ");
        if (actorId != null) {
            whereClause.append("and actor_id = :actorId ");
            params.addValue("actorId", actorId);
        }
        if (eventType != null && !eventType.isBlank()) {
            whereClause.append("and action = :eventType ");
            params.addValue("eventType", eventType);
        }
        if (from != null) {
            whereClause.append("and occurred_at >= :from ");
            params.addValue("from", Timestamp.from(from));
        }
        if (to != null) {
            whereClause.append("and occurred_at <= :to ");
            params.addValue("to", Timestamp.from(to));
        }

        String countSql = "select count(*) from identity.audit_events " + whereClause;
        Long total = namedParameterJdbcTemplate.queryForObject(countSql, params, Long.class);

        String querySql = "select event_id, tenant_id, occurred_at, actor_id, actor_type, service, action, "
                + "entity_type, entity_id, payload_hash, evidence_id, metadata "
                + "from identity.audit_events "
            + whereClause + " order by occurred_at desc limit :limit offset :offset";

        List<AuditEventView> items = namedParameterJdbcTemplate.query(querySql, params, (rs, rowNum) -> {
            AuditEventView view = new AuditEventView();
            view.setEventId(rs.getObject("event_id", UUID.class));
            view.setTenantId(rs.getObject("tenant_id", UUID.class));
            view.setOccurredAt(rs.getTimestamp("occurred_at").toInstant());
            view.setActorId(rs.getObject("actor_id", UUID.class));
            view.setActorType(rs.getString("actor_type"));
            view.setService(rs.getString("service"));
            view.setAction(rs.getString("action"));
            view.setEntityType(rs.getString("entity_type"));
            view.setEntityId(rs.getString("entity_id"));
            view.setPayloadHash(rs.getString("payload_hash"));
            view.setEvidenceId(rs.getObject("evidence_id", UUID.class));
            String metadata = rs.getString("metadata");
            try {
                JsonNode metadataNode = metadata != null ? objectMapper.readTree(metadata) : objectMapper.createObjectNode();
                view.setMetadata(metadataNode);
            } catch (Exception e) {
                throw new IllegalStateException("AUDIT_METADATA_PARSE_FAILED", e);
            }
            return view;
        });

        AuditEventPageResponse response = new AuditEventPageResponse();
        response.setItems(items);
        response.setPage(page);
        response.setSize(size);
        response.setTotal(total != null ? total : 0L);
        return response;
    }
}