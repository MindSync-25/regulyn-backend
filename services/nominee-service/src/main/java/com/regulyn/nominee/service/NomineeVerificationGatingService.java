package com.regulyn.nominee.service;

import com.regulyn.nominee.dto.MissingStep;
import com.regulyn.nominee.entity.NomineeVerificationRequirement;
import com.regulyn.nominee.repository.NomineeDocumentRepository;
import com.regulyn.nominee.repository.NomineeVerificationRequirementRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NomineeVerificationGatingService {

    public static final String STEP_IDENTITY = "IDENTITY_PROOF";
    public static final String STEP_ADDRESS = "ADDRESS_PROOF";
    public static final String STEP_AUTHORIZATION = "AUTHORIZATION_LETTER";
    public static final String STEP_EXCEPTION = "VERIFICATION_EXCEPTION";

    private final NomineeVerificationRequirementRepository requirementRepository;
    private final NomineeDocumentRepository documentRepository;

    public NomineeVerificationGatingService(
            NomineeVerificationRequirementRepository requirementRepository,
            NomineeDocumentRepository documentRepository) {
        this.requirementRepository = requirementRepository;
        this.documentRepository = documentRepository;
    }

    public GatingResult evaluate(UUID tenantId, UUID nomineeId) {
        Map<String, Integer> requiredSteps = loadRequirements(tenantId);

        List<MissingStep> missingSteps = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : requiredSteps.entrySet()) {
            String step = entry.getKey();
            int minDocs = entry.getValue();
            long count = documentRepository.countByTenantIdAndNomineeIdAndVerificationStep(tenantId, nomineeId, step);
            if (count < minDocs) {
                missingSteps.add(new MissingStep(step, minDocs, (int) count));
            }
        }

        boolean exceptionPresent = documentRepository.countByTenantIdAndNomineeIdAndVerificationStep(
                tenantId, nomineeId, STEP_EXCEPTION) > 0;

        boolean ok = missingSteps.isEmpty() || exceptionPresent;
        return new GatingResult(ok, missingSteps, exceptionPresent);
    }

    private Map<String, Integer> loadRequirements(UUID tenantId) {
        List<NomineeVerificationRequirement> requirements = requirementRepository
                .findByIdTenantIdAndActiveTrue(tenantId);
        Map<String, Integer> requiredSteps = new LinkedHashMap<>();

        for (NomineeVerificationRequirement req : requirements) {
            if (!req.isRequired() || req.getId() == null) {
                continue;
            }
            requiredSteps.put(req.getId().getVerificationStep(), Math.max(0, req.getMinDocs()));
        }

        if (requiredSteps.isEmpty()) {
            requiredSteps.put(STEP_IDENTITY, 1);
            requiredSteps.put(STEP_ADDRESS, 1);
            requiredSteps.put(STEP_AUTHORIZATION, 1);
        }

        return requiredSteps;
    }

    public record GatingResult(boolean ok, List<MissingStep> missingSteps, boolean exceptionPresent) {
    }
}
