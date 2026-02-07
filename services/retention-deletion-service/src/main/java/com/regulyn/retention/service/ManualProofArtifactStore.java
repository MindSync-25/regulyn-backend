package com.regulyn.retention.service;

import com.regulyn.evidence.store.LocalFileSystemArtifactStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ManualProofArtifactStore {

    private final LocalFileSystemArtifactStore delegate;

    public ManualProofArtifactStore(
            @Value("${evidence.storage.manualProofDir:./data/manual-proofs}") String baseDir) {
        this.delegate = new LocalFileSystemArtifactStore(baseDir);
    }

    public String store(UUID tenantId, UUID executionId, String filename, byte[] bytes, String contentType) {
        return delegate.store(tenantId, executionId, filename, bytes, contentType);
    }
}