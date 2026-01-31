package com.regulyn.retention.service;

import com.regulyn.retention.entity.RetentionCandidate;
import com.regulyn.retention.entity.RetentionRule;
import com.regulyn.retention.model.CreateDeletionRequest;
import com.regulyn.retention.repository.RetentionCandidateRepository;
import com.regulyn.retention.repository.RetentionRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled job to auto-create deletion requests from retention candidates.
 * Runs daily at midnight to find candidates eligible for deletion based on retention rules.
 */
@Component
@ConditionalOnProperty(name = "retention.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

    private final RetentionRuleRepository retentionRuleRepository;
    private final RetentionCandidateRepository retentionCandidateRepository;
    private final DeletionWorkflowService deletionWorkflowService;

    public RetentionScheduler(
            RetentionRuleRepository retentionRuleRepository,
            RetentionCandidateRepository retentionCandidateRepository,
            DeletionWorkflowService deletionWorkflowService) {
        this.retentionRuleRepository = retentionRuleRepository;
        this.retentionCandidateRepository = retentionCandidateRepository;
        this.deletionWorkflowService = deletionWorkflowService;
    }

    @Scheduled(cron = "${retention.scheduler.cron:0 0 0 * * ?}") // Daily at midnight
    public void processRetentionCandidates() {
        log.info("Starting retention candidate processing");

        List<RetentionRule> activeRules = retentionRuleRepository.findAll().stream()
                .filter(RetentionRule::getEnabled)
                .toList();

        int candidatesProcessed = 0;
        int deletionsCreated = 0;

        for (RetentionRule rule : activeRules) {
            try {
                Instant threshold = Instant.now().minusSeconds(rule.getRetentionDays() * 24L * 60 * 60);

                List<RetentionCandidate> candidates = retentionCandidateRepository.findEligibleForRule(
                        rule.getTenantId(),
                        rule.getSubjectType(),
                        rule.getEntityType(),
                        threshold
                );

                for (RetentionCandidate candidate : candidates) {
                    try {
                        // Create deletion request with RETENTION source
                        CreateDeletionRequest request = new CreateDeletionRequest();
                        request.setSubjectId(candidate.getSubjectId());
                        request.setSubjectType(candidate.getSubjectType());
                        request.setEntityType(candidate.getEntityType());
                        request.setSource("RETENTION");
                        request.setReason("Auto-created from retention rule: " + rule.getRuleName());
                        request.setRequiresApproval(true);
                        request.setProofRequired(true);
                        request.setDueInDays(30);

                        // Use idempotency key to prevent duplicates
                        String idempotencyKey = "retention-" + rule.getRuleId() + "-" + candidate.getCandidateId();

                        // Use system user (could be configurable)
                        UUID systemUserId = UUID.fromString("00000000-0000-0000-0000-000000000000");

                        deletionWorkflowService.createDeletion(
                                rule.getTenantId(),
                                systemUserId,
                                request,
                                idempotencyKey
                        );

                        deletionsCreated++;
                        log.debug("Created deletion for candidate {} under rule {}", candidate.getCandidateId(), rule.getRuleName());

                    } catch (Exception e) {
                        log.error("Failed to create deletion for candidate {}: {}", candidate.getCandidateId(), e.getMessage());
                    }
                }

                candidatesProcessed += candidates.size();

            } catch (Exception e) {
                log.error("Failed to process retention rule {}: {}", rule.getRuleId(), e.getMessage());
            }
        }

        log.info("Retention candidate processing complete. Processed {} candidates, created {} deletions",
                candidatesProcessed, deletionsCreated);
    }
}
