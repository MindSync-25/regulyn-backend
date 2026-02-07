package com.regulyn.retention.cascade;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.regulyn.retention.entity.DeletionRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class DeletionPlanBuilder {

    private final DeletionSystemRegistry systemRegistry;
    private final ObjectMapper objectMapper;

    public DeletionPlanBuilder(DeletionSystemRegistry systemRegistry) {
        this.systemRegistry = systemRegistry;
        this.objectMapper = new ObjectMapper()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
    }

    public PlanResult buildPlan(UUID tenantId, DeletionRequest deletion, int planVersion) {
        List<DeletionSystemRegistry.SystemDefinition> matchedSystems = systemRegistry
                .getSystemsForEntityType(deletion.getEntityType());

        List<PlanSystem> systems = new ArrayList<>();
        for (DeletionSystemRegistry.SystemDefinition system : matchedSystems) {
            PlanSystem planSystem = new PlanSystem(
                    system.getSystemKey(),
                    deletion.getSubjectId().toString()
            );
            systems.add(planSystem);
        }

        systems.sort(Comparator
                .comparing(PlanSystem::systemKey)
                .thenComparing(PlanSystem::subjectRef));

        Map<String, Object> plan = new HashMap<>();
        plan.put("planVersion", planVersion);
        plan.put("deletionId", deletion.getDeletionId().toString());
        plan.put("tenantId", tenantId.toString());
        plan.put("subjectType", deletion.getSubjectType());
        plan.put("subjectId", deletion.getSubjectId().toString());
        plan.put("entityType", deletion.getEntityType());
        List<Map<String, Object>> systemsPayload = new ArrayList<>();
        for (PlanSystem system : systems) {
            Map<String, Object> systemPayload = new HashMap<>();
            systemPayload.put("systemKey", system.systemKey());
            systemPayload.put("subjectRef", system.subjectRef());
            systemPayload.put("capabilities", Map.of("supportsConnectorDeletion", true));
            systemsPayload.add(systemPayload);
        }
        plan.put("systems", systemsPayload);

        String planJson = toCanonicalJson(plan);
        String planHash = sha256Hex(planJson.getBytes(StandardCharsets.UTF_8));

        return new PlanResult(planJson, planHash, systems);
    }

    private String toCanonicalJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize plan JSON", e);
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute SHA-256 hash", e);
        }
    }

    public record PlanSystem(String systemKey, String subjectRef) {
    }

    public record PlanResult(String planJson, String planHash, List<PlanSystem> systems) {
    }
}
