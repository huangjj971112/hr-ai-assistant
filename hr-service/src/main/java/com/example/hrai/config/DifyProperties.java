package com.example.hrai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.dify")
public class DifyProperties {

    private boolean enabled;

    private String baseUrl = "https://api.dify.ai/v1";

    private String apiKey;

    private int timeoutSeconds = 60;
}
