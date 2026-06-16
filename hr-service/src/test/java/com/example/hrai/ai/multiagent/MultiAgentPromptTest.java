package com.example.hrai.ai.multiagent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MultiAgentPromptTest {

    @Test
    void coordinatorPromptShouldKeepSafetyAndJsonDispatchContract() throws Exception {
        String prompt = new String(
                getClass().getResourceAsStream("/prompts/multi-agent-coordinator-system.txt").readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertThat(prompt).contains("只负责理解用户问题");
        assertThat(prompt).contains("pending + confirm");
        assertThat(prompt).contains("只输出 JSON 调度计划");
        assertThat(prompt).contains("SalaryAgent");
    }
}
