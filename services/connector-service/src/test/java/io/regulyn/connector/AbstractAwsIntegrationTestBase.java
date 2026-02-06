package io.regulyn.connector;

import io.regulyn.connector.credentials.EncryptionService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SECRETSMANAGER;

/**
 * Base class for integration tests that require AWS LocalStack.
 * Extends AbstractIntegrationTestBase to include LocalStack container for AWS services.
 * 
 * <p>Use this base class for tests that need AWS Secrets Manager or other AWS services.</p>
 */
@Testcontainers
public abstract class AbstractAwsIntegrationTestBase extends AbstractIntegrationTestBase {

    /**
     * Shared LocalStack container for AWS services (Secrets Manager, etc.).
     * Static to ensure it starts once and is reused across test classes.
     */
    @Container
    protected static final LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(SECRETSMANAGER)
            .withReuse(false); // Disable reuse for clean state

    /**
     * Configure AWS-specific properties for LocalStack.
     */
    @DynamicPropertySource
    static void configureAwsProperties(DynamicPropertyRegistry registry) {
        // Generate test encryption key
        String testKey = EncryptionService.generateKey();
        registry.add("connector.credentials.encryption.key", () -> testKey);
        
        // Enable AWS and configure for LocalStack
        registry.add("connector.credentials.aws.enabled", () -> "true");
        registry.add("connector.credentials.aws.region", () -> localstack.getRegion());
        registry.add("connector.credentials.aws.secrets-endpoint", () -> 
                localstack.getEndpointOverride(SECRETSMANAGER).toString());
        registry.add("connector.credentials.aws.access-key", () -> localstack.getAccessKey());
        registry.add("connector.credentials.aws.secret-key", () -> localstack.getSecretKey());
    }
}
