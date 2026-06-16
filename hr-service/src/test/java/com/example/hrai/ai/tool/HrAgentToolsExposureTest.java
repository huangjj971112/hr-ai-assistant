package com.example.hrai.ai.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class HrAgentToolsExposureTest {

    @Test
    void shouldExposeOnlyQueryToolsToModel() {
        Set<String> exposedTools = Arrays.stream(HrAgentTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(exposedTools).containsExactlyInAnyOrder(
                "queryLeaveBalance",
                "queryAttendance",
                "queryLeavePolicy"
        );
        assertThat(exposedTools).doesNotContain("applyLeave");
    }
}
