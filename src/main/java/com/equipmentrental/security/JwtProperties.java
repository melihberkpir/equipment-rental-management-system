package com.equipmentrental.security;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotNull @Size(min = 32, message = "JWT_SECRET must be at least 32 characters") String secret,
        @NotNull Duration expiration) {
}
