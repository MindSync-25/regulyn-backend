package io.regulyn.connector.repository;

import io.regulyn.connector.model.ConnectorSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ConnectorScheduleRepository extends JpaRepository<ConnectorSchedule, UUID> {
    
    List<ConnectorSchedule> findByTenantIdAndEnabledTrue(UUID tenantId);
    
    List<ConnectorSchedule> findByConnectorIdAndEnabledTrue(UUID connectorId);
    
    /**
     * Find schedules that are due for execution using SELECT FOR UPDATE SKIP LOCKED.
     * This ensures only one scheduler instance claims each schedule.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
        SELECT s FROM ConnectorSchedule s 
        WHERE s.enabled = true 
        AND s.nextRunAt <= :now 
        ORDER BY s.nextRunAt ASC
        """)
    List<ConnectorSchedule> findDueSchedulesForUpdate(@Param("now") Instant now);
}
