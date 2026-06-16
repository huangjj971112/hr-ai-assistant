package com.example.hrai.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AgentModelTimeoutConfigurationTest {

    @Test
    void shouldApplyModelCallTimeoutToConnectAndReadTimeouts() {
        ClientHttpRequestFactorySettings settings = AgentModelTimeoutConfiguration.requestFactorySettings(
                Duration.ofSeconds(12)
        );

        assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(12));
        assertThat(settings.readTimeout()).isEqualTo(Duration.ofSeconds(12));
    }
}
