package io.regulyn.connector.credentials;

import io.regulyn.connector.model.ConnectorCredential;
import io.regulyn.connector.repository.ConnectorCredentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Local database credential resolver with AES-256 encryption at rest.
 * Uses a master key (from environment or configuration) to encrypt/decrypt credentials.
 */
@Component
public class LocalDbCredentialResolver implements CredentialResolver {
    private static final Logger logger = LoggerFactory.getLogger(LocalDbCredentialResolver.class);
    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;
    
    private final ConnectorCredentialRepository credentialRepository;
    private final SecretKey masterKey;
    
    public LocalDbCredentialResolver(ConnectorCredentialRepository credentialRepository) {
        this.credentialRepository = credentialRepository;
        this.masterKey = loadOrGenerateMasterKey();
    }
    
    @Override
    @Transactional(readOnly = true)
    public ResolvedCredential resolve(UUID tenantId, UUID connectorId, String credentialType) {
        ConnectorCredential credential = credentialRepository
            .findByConnectorIdAndCredentialType(connectorId, credentialType)
            .orElseThrow(() -> new CredentialNotFoundException(
                "Credential not found for connector: " + connectorId + ", type: " + credentialType
            ));
        
        String decryptedValue = decrypt(credential.getEncryptedValue());
        
        logger.debug("Resolved credential from local DB for connector: {}, type: {}", connectorId, credentialType);
        
        return ResolvedCredential.withMetadata(
            credentialType,
            decryptedValue,
            Map.of(
                "source", "local_db",
                "credential_id", credential.getCredentialId().toString(),
                "encryption_key_ref", credential.getEncryptionKeyRef()
            )
        );
    }
    
    @Override
    @Transactional
    public void store(UUID tenantId, UUID connectorId, String credentialType, String value) {
        byte[] encryptedValue = encrypt(value);
        
        ConnectorCredential credential = credentialRepository
            .findByConnectorIdAndCredentialType(connectorId, credentialType)
            .orElse(new ConnectorCredential());
        
        credential.setTenantId(tenantId);
        credential.setConnectorId(connectorId);
        credential.setCredentialType(credentialType);
        credential.setEncryptedValue(encryptedValue);
        credential.setEncryptionKeyRef("master_key_v1");
        
        credentialRepository.save(credential);
        
        logger.info("Stored encrypted credential for connector: {}, type: {}", connectorId, credentialType);
    }
    
    @Override
    @Transactional
    public void delete(UUID tenantId, UUID connectorId, String credentialType) {
        credentialRepository.deleteByConnectorIdAndCredentialType(connectorId, credentialType);
        logger.info("Deleted credential for connector: {}, type: {}", connectorId, credentialType);
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID tenantId, UUID connectorId, String credentialType) {
        return credentialRepository.existsByConnectorIdAndCredentialType(connectorId, credentialType);
    }
    
    private byte[] encrypt(String value) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey);
            return cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new CredentialResolutionException("Failed to encrypt credential", e);
        }
    }
    
    private String decrypt(byte[] encryptedValue) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, masterKey);
            byte[] decrypted = cipher.doFinal(encryptedValue);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CredentialResolutionException("Failed to decrypt credential", e);
        }
    }
    
    private SecretKey loadOrGenerateMasterKey() {
        // In production, this should be loaded from a secure key management system
        // For now, we'll use an environment variable or generate a key
        String keyEnv = System.getenv("CONNECTOR_MASTER_KEY");
        
        if (keyEnv != null && !keyEnv.isBlank()) {
            byte[] keyBytes = Base64.getDecoder().decode(keyEnv);
            return new SecretKeySpec(keyBytes, ALGORITHM);
        }
        
        // Generate a new key for development/testing
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE, new SecureRandom());
            SecretKey key = keyGen.generateKey();
            
            String encodedKey = Base64.getEncoder().encodeToString(key.getEncoded());
            logger.warn("Generated new master key. Set CONNECTOR_MASTER_KEY={} in production", encodedKey);
            
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate master key", e);
        }
    }
}
