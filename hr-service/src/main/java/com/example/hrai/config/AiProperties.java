package com.example.hrai.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    @NotBlank
    private String provider;

    @NotBlank
    private String model;
}
