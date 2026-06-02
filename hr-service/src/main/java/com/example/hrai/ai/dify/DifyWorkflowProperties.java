package com.example.hrai.ai.dify;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "dify")
public class DifyWorkflowProperties {

    private boolean enabled;

    private String baseUrl = "https://api.dify.ai/v1";

    private String apiKey;

    private String workflowPath = "/workflows/run";

    private int timeoutSeconds = 60;
}
