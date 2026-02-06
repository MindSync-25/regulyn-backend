package io.regulyn.connector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the connector scheduler engine.
 */
@Configuration
@ConfigurationProperties(prefix = "connector.scheduler")
public class SchedulerConfig {

    /**
     * Maximum number of schedules to claim in a single batch.
     * Default: 50
     */
    private int batchSize = 50;

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
