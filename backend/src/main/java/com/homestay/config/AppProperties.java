package com.homestay.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        String frontendUrl,
        String mailFrom,
        List<String> corsOrigins,
        Jwt jwt,
        BootstrapAdmin bootstrapAdmin,
        Storage storage) {

    public record Jwt(String secret, int accessTokenMinutes, int refreshTokenDays) {}

    public record BootstrapAdmin(String email, String password) {}

    public record Storage(String endpoint, String accessKey, String secretKey, String bucket) {}
}
