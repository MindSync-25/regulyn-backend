package io.regulyn.connector.config;

import io.regulyn.connector.config.CredentialConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;

import java.net.URI;

/**
 * Configuration for AWS Secrets Manager client.
 * Only created when connector.credentials.aws.enabled=true.
 */
@Configuration
@ConditionalOnProperty(name = "connector.credentials.aws.enabled", havingValue = "true")
public class AwsSecretsManagerConfig {

    private static final Logger logger = LoggerFactory.getLogger(AwsSecretsManagerConfig.class);

    @Bean
    public SecretsManagerClient secretsManagerClient(CredentialConfig credentialConfig) {
        CredentialConfig.Aws awsConfig = credentialConfig.getAws();

        SecretsManagerClientBuilder builder = SecretsManagerClient.builder()
                .region(Region.of(awsConfig.getRegion()));

        // Credentials provider
        AwsCredentialsProvider credentialsProvider;
        if (awsConfig.getAccessKey() != null && !awsConfig.getAccessKey().isEmpty()) {
            // Use static credentials (for LocalStack tests)
            logger.warn("Using static AWS credentials - this should only be used for testing");
            credentialsProvider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(awsConfig.getAccessKey(), awsConfig.getSecretKey())
            );
        } else {
            // Use default credentials provider chain (IAM roles, env vars, etc.)
            credentialsProvider = DefaultCredentialsProvider.create();
        }
        builder.credentialsProvider(credentialsProvider);

        // Endpoint override (for LocalStack)
        if (awsConfig.getSecretsEndpoint() != null && !awsConfig.getSecretsEndpoint().isEmpty()) {
            logger.info("Using AWS Secrets Manager endpoint override: {}", awsConfig.getSecretsEndpoint());
            builder.endpointOverride(URI.create(awsConfig.getSecretsEndpoint()));
        }

        SecretsManagerClient client = builder.build();
        logger.info("AWS Secrets Manager client initialized for region: {}", awsConfig.getRegion());
        return client;
    }
}
