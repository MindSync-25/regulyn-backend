package io.regulyn.connector.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;

/**
 * AWS SDK configuration for Secrets Manager client.
 */
@Configuration
@ConditionalOnProperty(name = "aws.secrets.enabled", havingValue = "true")
public class AwsClientConfig {
    
    @Bean
    public SecretsManagerClient secretsManagerClient(AwsSecretsConfig config) {
        SecretsManagerClientBuilder builder = SecretsManagerClient.builder()
            .region(Region.of(config.getRegion()))
            .credentialsProvider(DefaultCredentialsProvider.create());
        
        // Use endpoint override for LocalStack or custom endpoints
        if (config.getEndpointOverride() != null) {
            builder.endpointOverride(config.getEndpointOverride());
        }
        
        return builder.build();
    }
}
