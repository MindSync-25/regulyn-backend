package io.regulyn.connector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the connector run worker.
 */
@Configuration
@ConfigurationProperties(prefix = "connector.run-worker")
public class RunWorkerConfig {

    /**
     * Maximum number of runs to claim in a single batch.
     * Default: 50
     */
    private int batchSize = 50;

    /**
     * Timeout in minutes after which a RUNNING run is considered stuck.
     * Default: 15 minutes
     */
    private int stuckTimeoutMinutes = 15;

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getStuckTimeoutMinutes() {
        return stuckTimeoutMinutes;
    }

    public void setStuckTimeoutMinutes(int stuckTimeoutMinutes) {
        this.stuckTimeoutMinutes = stuckTimeoutMinutes;
    }
}
