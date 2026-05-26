package com.example.hraigateway.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.gateway")
public class GatewayProperties {

    @NotBlank
    private String backendBaseUrl;

    @NotBlank
    private String jwtSecret;
}
