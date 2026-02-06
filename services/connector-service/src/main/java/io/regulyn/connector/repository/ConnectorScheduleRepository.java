package io.regulyn.connector.repository;

import io.regulyn.connector.model.ConnectorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for ConnectorSchedule entities.
 */
@Repository
public interface ConnectorScheduleRepository extends JpaRepository<ConnectorSchedule, UUID> {

    /**
     * Find schedules that are due to fire.
     * Returns enabled schedules where next_fire_at <= now, ordered by next_fire_at ascending.
     * 
     * @param now Current timestamp
     * @return List of due schedules
     */
    @Query("SELECT s FROM ConnectorSchedule s WHERE s.enabled = true AND s.nextFireAt <= :now ORDER BY s.nextFireAt ASC")
    List<ConnectorSchedule> findDueSchedules(@Param("now") Instant now);
}
