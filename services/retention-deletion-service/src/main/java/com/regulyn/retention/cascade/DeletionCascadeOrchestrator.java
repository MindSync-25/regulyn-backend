package com.regulyn.retention.cascade;

import com.regulyn.retention.entity.DeletionExecutionPlan;
import com.regulyn.retention.entity.DeletionRequest;
import com.regulyn.retention.entity.DeletionSystemExecution;
import com.regulyn.retention.enums.DeletionExecutionPlanStatus;
import com.regulyn.retention.enums.DeletionSystemExecutionStatus;
import com.regulyn.retention.model.CascadeExecuteResponse;
import com.regulyn.retention.model.CascadeSystemExecutionResponse;
import com.regulyn.retention.repository.DeletionExecutionPlanRepository;
import com.regulyn.retention.repository.DeletionRequestRepository;
import com.regulyn.retention.repository.DeletionSystemExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DeletionCascadeOrchestrator {

    private final DeletionRequestRepository deletionRequestRepository;
    private final DeletionExecutionPlanRepository planRepository;
    private final DeletionSystemExecutionRepository executionRepository;
    private final DeletionPlanBuilder planBuilder;
    private final DeletionCascadeExecutor executor;
    private final CascadeEventWriter eventWriter;

    public DeletionCascadeOrchestrator(
            DeletionRequestRepository deletionRequestRepository,
            DeletionExecutionPlanRepository planRepository,
            DeletionSystemExecutionRepository executionRepository,
            DeletionPlanBuilder planBuilder,
            DeletionCascadeExecutor executor,
            CascadeEventWriter eventWriter) {
        this.deletionRequestRepository = deletionRequestRepository;
        this.planRepository = planRepository;
        this.executionRepository = executionRepository;
        this.planBuilder = planBuilder;
        this.executor = executor;
        this.eventWriter = eventWriter;
    }

    @Transactional
    public CascadeExecuteResponse executeCascade(UUID tenantId, UUID actorId, UUID deletionId, String idempotencyKey) {
        DeletionRequest deletion = deletionRequestRepository.findByDeletionIdAndTenantId(deletionId, tenantId)
                .orElseThrow(() -> new DeletionNotFoundException("Deletion request not found"));

        if ("CLOSED".equalsIgnoreCase(deletion.getStatus())) {
            throw new DeletionClosedException("Deletion request is closed");
        }
        if (Boolean.TRUE.equals(deletion.getRequiresApproval()) && !"APPROVED".equalsIgnoreCase(deletion.getStatus())) {
            throw new DeletionApprovalRequiredException("Deletion requires approval before cascade execution");
        }

        Optional<DeletionExecutionPlan> existingByKey = planRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (existingByKey.isPresent()) {
            DeletionExecutionPlan existing = existingByKey.get();
            if (!existing.getDeletionId().equals(deletionId)) {
                throw new IdempotencyKeyConflictException("Idempotency key already used for a different deletion");
            }
            List<DeletionSystemExecution> executions = executionRepository.findByTenantIdAndPlanId(tenantId, existing.getPlanId());
            return toResponse(deletionId, existing, executions);
        }

        int nextPlanVersion = determineNextPlanVersion(tenantId, deletionId);
        DeletionPlanBuilder.PlanResult planResult = planBuilder.buildPlan(tenantId, deletion, nextPlanVersion);
        if (planResult.systems().isEmpty()) {
            throw new NoSystemsConfiguredException("No systems configured for entityType: " + deletion.getEntityType());
        }

        DeletionExecutionPlan plan = new DeletionExecutionPlan();
        plan.setTenantId(tenantId);
        plan.setDeletionId(deletionId);
        plan.setIdempotencyKey(idempotencyKey);
        plan.setPlanVersion(nextPlanVersion);
        plan.setPlanStatus(DeletionExecutionPlanStatus.ACTIVE);
        plan.setPlanHashSha256(planResult.planHash());
        plan.setPlanJson(planResult.planJson());
        plan.setCreatedBy(actorId);
        plan.setUpdatedBy(actorId);
        plan = planRepository.save(plan);

        supersedeActivePlans(tenantId, deletionId, plan.getPlanId(), actorId);

        for (DeletionPlanBuilder.PlanSystem system : planResult.systems()) {
            Optional<DeletionSystemExecution> existing = executionRepository
                    .findByTenantIdAndPlanIdAndSystemKeyAndSubjectRef(tenantId, plan.getPlanId(), system.systemKey(), system.subjectRef());
            if (existing.isEmpty()) {
                DeletionSystemExecution execution = new DeletionSystemExecution();
                execution.setTenantId(tenantId);
                execution.setDeletionId(deletionId);
                execution.setPlanId(plan.getPlanId());
                execution.setSystemKey(system.systemKey());
                execution.setSubjectRef(system.subjectRef());
                execution.setExecutionStatus(DeletionSystemExecutionStatus.PENDING);
                execution.setAttemptCount(0);
                execution.setMaxAttempts(8);
                execution.setCreatedBy(actorId);
                execution.setUpdatedBy(actorId);
                executionRepository.save(execution);
            }
        }

        List<String> systemKeys = planResult.systems().stream()
                .map(DeletionPlanBuilder.PlanSystem::systemKey)
                .collect(Collectors.toList());

        Map<String, Object> planPayload = new HashMap<>();
        planPayload.put("tenantId", tenantId);
        planPayload.put("actorId", actorId);
        planPayload.put("deletionId", deletionId);
        planPayload.put("planId", plan.getPlanId());
        planPayload.put("planVersion", plan.getPlanVersion());
        planPayload.put("planHash", plan.getPlanHashSha256());
        planPayload.put("systemKeys", systemKeys);

        eventWriter.writeAudit(tenantId, actorId, DeletionCascadeAuditActions.DELETION_PLAN_CREATED,
                "DeletionRequest", deletionId, planPayload);
        eventWriter.writeOutbox(tenantId, actorId, DeletionCascadeEventTypes.DELETION_PLAN_CREATED,
                "deletion_request", deletionId, planPayload, idempotencyKey);

        eventWriter.writeAudit(tenantId, actorId, DeletionCascadeAuditActions.DELETION_CASCADE_STARTED,
                "DeletionRequest", deletionId, planPayload);
        eventWriter.writeOutbox(tenantId, actorId, DeletionCascadeEventTypes.DELETION_CASCADE_STARTED,
                "deletion_request", deletionId, planPayload, idempotencyKey);

        executor.kickoffPendingExecutions(tenantId, actorId, plan.getPlanId(), idempotencyKey);

        List<DeletionSystemExecution> executions = executionRepository.findByTenantIdAndPlanId(tenantId, plan.getPlanId());
        return toResponse(deletionId, plan, executions);
    }

    private int determineNextPlanVersion(UUID tenantId, UUID deletionId) {
        List<DeletionExecutionPlan> plans = planRepository.findByTenantIdAndDeletionIdOrderByPlanVersionDesc(tenantId, deletionId);
        if (plans.isEmpty()) {
            return 1;
        }
        return plans.get(0).getPlanVersion() + 1;
    }

    private void supersedeActivePlans(UUID tenantId, UUID deletionId, UUID newPlanId, UUID actorId) {
        List<DeletionExecutionPlan> plans = planRepository.findByTenantIdAndDeletionIdOrderByPlanVersionDesc(tenantId, deletionId);
        for (DeletionExecutionPlan plan : plans) {
            if (plan.getPlanStatus() == DeletionExecutionPlanStatus.ACTIVE && !plan.getPlanId().equals(newPlanId)) {
                plan.setPlanStatus(DeletionExecutionPlanStatus.SUPERSEDED);
                plan.setUpdatedBy(actorId);
                planRepository.save(plan);
            }
        }
    }

    private CascadeExecuteResponse toResponse(UUID deletionId, DeletionExecutionPlan plan, List<DeletionSystemExecution> executions) {
        CascadeExecuteResponse response = new CascadeExecuteResponse();
        response.setDeletionId(deletionId);
        response.setPlanId(plan.getPlanId());
        response.setPlanVersion(plan.getPlanVersion());
        response.setPlanHash(plan.getPlanHashSha256());

        List<CascadeSystemExecutionResponse> systems = executions.stream().map(execution -> {
            CascadeSystemExecutionResponse system = new CascadeSystemExecutionResponse();
            system.setSystemKey(execution.getSystemKey());
            system.setSubjectRef(execution.getSubjectRef());
            system.setStatus(execution.getExecutionStatus().name());
            system.setAttemptCount(execution.getAttemptCount());
            system.setExternalJobRef(execution.getExternalJobRef());
            return system;
        }).collect(Collectors.toList());

        response.setSystems(systems);
        return response;
    }
}
