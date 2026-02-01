package io.regulyn.scanner.service;

import com.regulyn.auth.context.TenantContextHolder;
import io.regulyn.scanner.dto.FindingResponse;
import io.regulyn.scanner.model.ScanFinding;
import io.regulyn.scanner.repository.ScanFindingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FindingService {

    private final ScanFindingRepository scanFindingRepository;

    public FindingService(ScanFindingRepository scanFindingRepository) {
        this.scanFindingRepository = scanFindingRepository;
    }

    @Transactional(readOnly = true)
    public List<FindingResponse> getRunFindings(UUID runId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        List<ScanFinding> findings = scanFindingRepository.findByTenantIdAndRunIdOrderByCreatedAtDesc(tenantId, runId);
        return findings.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private FindingResponse toResponse(ScanFinding finding) {
        FindingResponse response = new FindingResponse();
        response.setFindingId(finding.getFindingId());
        response.setRunId(finding.getRunId());
        response.setFindingType(finding.getFindingType());
        response.setEntityType(finding.getEntityType());
        response.setSubjectId(finding.getSubjectId());
        response.setFieldName(finding.getFieldName());
        response.setDataCategory(finding.getDataCategory());
        response.setRiskLevel(finding.getRiskLevel());
        response.setConfidence(finding.getConfidence());
        response.setDetails(finding.getDetails());
        response.setCreatedAt(finding.getCreatedAt());
        return response;
    }
}
