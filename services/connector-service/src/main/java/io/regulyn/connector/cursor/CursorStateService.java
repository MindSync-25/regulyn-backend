package io.regulyn.connector.cursor;

import io.regulyn.connector.model.ConnectorCursorState;
import io.regulyn.connector.repository.ConnectorCursorStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing connector cursor state.
 */
@Service
public class CursorStateService {

    private final ConnectorCursorStateRepository cursorRepository;

    public CursorStateService(ConnectorCursorStateRepository cursorRepository) {
        this.cursorRepository = cursorRepository;
    }

    /**
     * Get or create cursor state for a connector/target/job type.
     */
    @Transactional
    public ConnectorCursorState getOrCreateCursorState(
            UUID tenantId,
            UUID connectorId,
            UUID targetId,
            ConnectorCursorState.JobType jobType) {

        Optional<ConnectorCursorState> existing = cursorRepository.findByTenantIdAndConnectorIdAndTargetIdAndJobType(
                tenantId, connectorId, targetId, jobType);

        if (existing.isPresent()) {
            return existing.get();
        }

        ConnectorCursorState cursorState = new ConnectorCursorState();
        cursorState.setTenantId(tenantId);
        cursorState.setConnectorId(connectorId);
        cursorState.setTargetId(targetId);
        cursorState.setJobType(jobType);
        cursorState.setCursorJson(new HashMap<>());

        return cursorRepository.save(cursorState);
    }

    /**
     * Update cursor JSON after a successful run.
     */
    @Transactional
    public ConnectorCursorState updateCursorState(ConnectorCursorState cursorState, Map<String, Object> newCursorJson) {
        if (newCursorJson == null) {
            return cursorState;
        }
        cursorState.setCursorJson(new HashMap<>(newCursorJson));
        return cursorRepository.save(cursorState);
    }
}
