package com.regulyn.evidence.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Local filesystem implementation of EvidenceArtifactStore.
 * Stores artifacts under: {baseDir}/{tenantId}/{evidenceId}/{filename}
 * Enforces immutability - will not overwrite existing files.
 */
@Component
public class LocalFileSystemArtifactStore implements EvidenceArtifactStore {
    
    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemArtifactStore.class);
    
    private final Path baseDirectory;
    
    public LocalFileSystemArtifactStore(
            @Value("${evidence.storage.localDir:./data/evidence}") String baseDir) {
        this.baseDirectory = Paths.get(baseDir).toAbsolutePath();
        ensureBaseDirectoryExists();
        log.info("Evidence artifact store initialized at: {}", baseDirectory);
    }
    
    @Override
    public String store(UUID tenantId, UUID evidenceId, String filename, byte[] bytes, String contentType) {
        if (tenantId == null || evidenceId == null || filename == null || bytes == null) {
            throw new IllegalArgumentException("All parameters must be non-null");
        }
        
        // Sanitize filename to prevent directory traversal
        String sanitizedFilename = sanitizeFilename(filename);
        
        // Build path: {baseDir}/{tenantId}/{evidenceId}/{filename}
        Path artifactPath = baseDirectory
                .resolve(tenantId.toString())
                .resolve(evidenceId.toString())
                .resolve(sanitizedFilename);
        
        // Enforce immutability
        if (Files.exists(artifactPath)) {
            throw new IllegalStateException("Artifact already exists (immutability violation): " + artifactPath);
        }
        
        try {
            // Ensure parent directories exist
            Files.createDirectories(artifactPath.getParent());
            
            // Write file
            Files.write(artifactPath, bytes, StandardOpenOption.CREATE_NEW);
            
            log.info("Stored evidence artifact: tenantId={}, evidenceId={}, filename={}, size={} bytes",
                    tenantId, evidenceId, sanitizedFilename, bytes.length);
            
            // Return relative path as artifact reference
            return baseDirectory.relativize(artifactPath).toString();
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to store evidence artifact: " + artifactPath, e);
        }
    }
    
    @Override
    public byte[] read(UUID tenantId, String artifactRef) {
        if (tenantId == null || artifactRef == null) {
            throw new IllegalArgumentException("tenantId and artifactRef must be non-null");
        }
        
        Path artifactPath = baseDirectory.resolve(artifactRef);
        
        // Security check: ensure path is within tenant's directory
        if (!artifactPath.startsWith(baseDirectory.resolve(tenantId.toString()))) {
            throw new IllegalArgumentException("Access denied: artifact not owned by tenant");
        }
        
        if (!Files.exists(artifactPath)) {
            throw new IllegalArgumentException("Artifact not found: " + artifactRef);
        }
        
        try {
            return Files.readAllBytes(artifactPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read evidence artifact: " + artifactPath, e);
        }
    }
    
    @Override
    public boolean exists(UUID tenantId, String artifactRef) {
        if (tenantId == null || artifactRef == null) {
            return false;
        }
        
        Path artifactPath = baseDirectory.resolve(artifactRef);
        
        // Security check
        if (!artifactPath.startsWith(baseDirectory.resolve(tenantId.toString()))) {
            return false;
        }
        
        return Files.exists(artifactPath) && Files.isRegularFile(artifactPath);
    }
    
    private void ensureBaseDirectoryExists() {
        try {
            if (!Files.exists(baseDirectory)) {
                Files.createDirectories(baseDirectory);
                log.info("Created evidence storage directory: {}", baseDirectory);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create evidence storage directory: " + baseDirectory, e);
        }
    }
    
    private String sanitizeFilename(String filename) {
        // Remove any path separators to prevent directory traversal
        return filename.replaceAll("[/\\\\]", "_");
    }
}
