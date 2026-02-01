package com.regulyn.employee.scheduler;

import com.regulyn.employee.model.EmployeeRequest;
import com.regulyn.employee.repository.EmployeeRequestRepository;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class SLAMonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(SLAMonitoringScheduler.class);

    private final EmployeeRequestRepository employeeRequestRepository;
    private final OutboxWriter outboxWriter;

    public SLAMonitoringScheduler(EmployeeRequestRepository employeeRequestRepository,
                                   OutboxWriter outboxWriter) {
        this.employeeRequestRepository = employeeRequestRepository;
        this.outboxWriter = outboxWriter;
    }

    @Scheduled(cron = "0 */15 * * * *") // Every 15 minutes
    @Transactional
    public void detectSLABreaches() {
        Instant now = Instant.now();
        List<EmployeeRequest> breachedRequests = employeeRequestRepository.findSLABreachedCandidates(now);

        if (breachedRequests.isEmpty()) {
            log.debug("No SLA breaches detected at {}", now);
            return;
        }

        log.warn("Detected {} SLA breaches at {}", breachedRequests.size(), now);

        for (EmployeeRequest request : breachedRequests) {
            request.setSlaBreached(true);
            employeeRequestRepository.save(request);

            // Emit SLA breach event
            EventEnvelopeV1 event = EventFactory.create(
                "employee_request.sla_breached",
                "employee-data-service",
                "employee_request",
                request.getRequestId().toString(),
                Map.of(
                    "tenantId", request.getTenantId().toString(),
                    "requestId", request.getRequestId().toString(),
                    "employeeId", request.getEmployeeId().toString(),
                    "requestType", request.getRequestType().name(),
                    "status", request.getStatus().name(),
                    "createdAt", request.getCreatedAt().toString(),
                    "dueAt", request.getDueAt().toString(),
                    "breachedAt", now.toString()
                )
            );
            outboxWriter.write(event);

            log.info("Marked request {} as SLA breached (due: {}, now: {})",
                request.getRequestId(), request.getDueAt(), now);
        }

        log.info("Processed {} SLA breaches successfully", breachedRequests.size());
    }
}
