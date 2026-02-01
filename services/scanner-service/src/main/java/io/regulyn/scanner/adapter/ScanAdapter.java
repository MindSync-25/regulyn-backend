package io.regulyn.scanner.adapter;

import io.regulyn.scanner.adapter.model.Finding;
import io.regulyn.scanner.model.ScanSource;

import java.time.Instant;
import java.util.List;

public interface ScanAdapter {

    /**
     * Run inventory scan to discover entities and fields
     */
    List<Finding> runInventory(ScanSource source, Instant since);

    /**
     * Run retention candidates scan to identify subjects for retention
     */
    List<Finding> runRetentionCandidates(ScanSource source, Instant since);
}
