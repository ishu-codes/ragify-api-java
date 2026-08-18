package ragify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
    String jwtSecret,
    long jwtExpiresMinutes,
    String corsOrigins,
    String storagePath,
    String grpcEndpoint
) {}
