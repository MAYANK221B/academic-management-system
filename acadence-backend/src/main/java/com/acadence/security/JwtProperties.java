package com.acadence.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "acadence.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTokenExpiration,
        @NotBlank String privateKeyPath,
        @NotBlank String publicKeyPath
) {
    public JwtProperties {
        if (accessTokenExpiration != null && (accessTokenExpiration.isZero() || accessTokenExpiration.isNegative())) {
            throw new IllegalArgumentException("acadence.jwt.access-token-expiration must be positive");
        }
    }

    // Avoid exposing key locations in generated record diagnostics.
    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", audience=" + audience + "]";
    }
}
