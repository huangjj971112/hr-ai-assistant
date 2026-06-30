package com.example.hrai.ai.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class HrMcpToolsExposureTest {

    @Test
    void shouldExposeExactlyApprovedMcpToolsWithoutDirectSubmit() {
        Set<String> toolNames = Arrays.stream(HrMcpTools.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Tool.class))
                .filter(annotation -> annotation != null)
                .map(Tool::name)
                .collect(Collectors.toSet());

        assertThat(toolNames).containsExactlyInAnyOrder(
                "query_leave_balance",
                "query_attendance",
                "query_leave_policy",
                "query_salary",
                "create_leave_pending",
                "confirm_leave_apply",
                "cancel_pending"
        );
        assertThat(toolNames).noneMatch(name -> name.equals("submit_leave") || name.equals("apply_leave"));
    }

    @Test
    void shouldDelegateEveryMcpToolToService() {
        assertThat(Arrays.stream(HrMcpTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .map(Method::getReturnType))
                .allMatch(type -> type.getSimpleName().equals("ToolResult"));
    }
}
