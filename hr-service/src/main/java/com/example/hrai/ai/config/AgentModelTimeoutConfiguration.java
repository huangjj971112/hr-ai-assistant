package com.example.hrai.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AgentModelTimeoutConfiguration {

    @Bean
    public RestClientCustomizer agentModelRestClientTimeoutCustomizer(
            @Value("${app.ai.model-call-timeout:30s}") Duration modelCallTimeout
    ) {
        ClientHttpRequestFactorySettings settings = requestFactorySettings(modelCallTimeout);
        return builder -> builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
    }

    static ClientHttpRequestFactorySettings requestFactorySettings(Duration modelCallTimeout) {
        return ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(modelCallTimeout)
                .withReadTimeout(modelCallTimeout);
    }
}
