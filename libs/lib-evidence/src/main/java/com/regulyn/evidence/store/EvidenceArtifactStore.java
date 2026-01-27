package com.regulyn.evidence.store;

import java.util.UUID;

/**
 * Interface for storing and retrieving evidence artifacts.
 * Implementations must ensure immutability - artifacts can never be overwritten.
 */
public interface EvidenceArtifactStore {
    
    /**
     * Store an evidence artifact.
     * 
     * @param tenantId The tenant ID owning this evidence
     * @param evidenceId The evidence record ID
     * @param filename Original filename
     * @param bytes File content
     * @param contentType MIME content type
     * @return Artifact reference string (used to retrieve the artifact later)
     * @throws IllegalStateException if artifact already exists (immutability)
     */
    String store(UUID tenantId, UUID evidenceId, String filename, byte[] bytes, String contentType);
    
    /**
     * Read an evidence artifact.
     * 
     * @param tenantId The tenant ID owning this evidence
     * @param artifactRef The artifact reference returned by store()
     * @return File content bytes
     * @throws IllegalArgumentException if artifact not found or access denied
     */
    byte[] read(UUID tenantId, String artifactRef);
    
    /**
     * Check if an artifact exists.
     * 
     * @param tenantId The tenant ID
     * @param artifactRef The artifact reference
     * @return true if artifact exists and is readable
     */
    boolean exists(UUID tenantId, String artifactRef);
}
