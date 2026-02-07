package com.regulyn.retention.cascade;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "deletion.cascade")
public class DeletionSystemRegistry {

    private List<SystemDefinition> systems = new ArrayList<>();

    public List<SystemDefinition> getSystems() {
        return systems;
    }

    public void setSystems(List<SystemDefinition> systems) {
        this.systems = systems != null ? systems : new ArrayList<>();
    }

    public List<SystemDefinition> getSystemsForEntityType(String entityType) {
        if (entityType == null || systems == null) {
            return Collections.emptyList();
        }
        String normalized = entityType.trim().toUpperCase(Locale.ROOT);
        return systems.stream()
                .filter(system -> system.getEntityTypes().stream()
                        .map(type -> type.trim().toUpperCase(Locale.ROOT))
                        .anyMatch(type -> type.equals(normalized)))
                .collect(Collectors.toList());
    }

    public static class SystemDefinition {
        private String systemKey;
        private List<String> entityTypes = new ArrayList<>();

        public String getSystemKey() {
            return systemKey;
        }

        public void setSystemKey(String systemKey) {
            this.systemKey = systemKey;
        }

        public List<String> getEntityTypes() {
            return entityTypes;
        }

        public void setEntityTypes(List<String> entityTypes) {
            this.entityTypes = entityTypes != null ? entityTypes : new ArrayList<>();
        }
    }
}
