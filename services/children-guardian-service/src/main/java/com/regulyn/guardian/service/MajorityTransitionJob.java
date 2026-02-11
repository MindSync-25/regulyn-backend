package com.regulyn.guardian.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.guardian.repository.GuardianConsentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class MajorityTransitionJob {

    private static final Logger logger = LoggerFactory.getLogger(MajorityTransitionJob.class);

    private final MajorityService majorityService;
    private final GuardianConsentRepository consentRepository;

    public MajorityTransitionJob(MajorityService majorityService, GuardianConsentRepository consentRepository) {
        this.majorityService = majorityService;
        this.consentRepository = consentRepository;
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void runDaily() {
        List<UUID> tenantIds = majorityService.loadTenantIdsForApprovedConsents();
        for (UUID tenantId : tenantIds) {
            try {
                TenantContextHolder.setContext(new TenantContext(tenantId, null, null, null, null));
                processTenant(tenantId, LocalDate.now());
            } catch (Exception e) {
                logger.error("Majority transition job failed for tenant {}", tenantId, e);
            } finally {
                TenantContextHolder.clear();
            }
        }
    }

    private void processTenant(UUID tenantId, LocalDate evaluationDate) {
        Set<UUID> childIds = consentRepository.findByTenantIdAndStatus(tenantId, "APPROVED")
                .stream()
                .map(consent -> consent.getChildId())
                .collect(Collectors.toSet());

        for (UUID childId : childIds) {
            try {
                majorityService.processMajorityTransition(tenantId, childId, evaluationDate);
            } catch (Exception e) {
                logger.error("Majority transition failed for child {}", childId, e);
            }
        }
    }
}
