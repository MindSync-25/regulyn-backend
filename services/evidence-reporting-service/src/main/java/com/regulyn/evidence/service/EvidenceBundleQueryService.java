package com.regulyn.evidence.service;

import com.regulyn.evidence.dto.EvidenceBundlePageResponse;
import com.regulyn.evidence.dto.EvidenceBundleSummary;
import com.regulyn.evidence.entity.EvidenceBundle;
import com.regulyn.evidence.repository.EvidenceBundleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EvidenceBundleQueryService {

    private final EvidenceBundleRepository bundleRepository;

    public EvidenceBundleQueryService(EvidenceBundleRepository bundleRepository) {
        this.bundleRepository = bundleRepository;
    }

    public EvidenceBundlePageResponse listBundles(UUID tenantId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<EvidenceBundle> bundlePage = bundleRepository.findByTenantId(tenantId, pageable);

        List<EvidenceBundleSummary> content = bundlePage.getContent().stream().map(bundle -> {
            EvidenceBundleSummary summary = new EvidenceBundleSummary();
            summary.setBundleId(bundle.getBundleId());
            summary.setType(bundle.getBundleType());
            summary.setCreatedAt(bundle.getCreatedAt());
            summary.setStatus(bundle.getStatus());
            return summary;
        }).collect(Collectors.toList());

        EvidenceBundlePageResponse response = new EvidenceBundlePageResponse();
        response.setContent(content);
        response.setTotalElements(bundlePage.getTotalElements());
        response.setTotalPages(bundlePage.getTotalPages());
        response.setSize(bundlePage.getSize());
        response.setNumber(bundlePage.getNumber());
        return response;
    }
}
