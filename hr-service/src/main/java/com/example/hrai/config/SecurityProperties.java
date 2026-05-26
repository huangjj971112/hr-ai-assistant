package com.example.hrai.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    @NotBlank
    private String jwtSecret;

    @Min(60)
    private long jwtExpirationSeconds;
}
