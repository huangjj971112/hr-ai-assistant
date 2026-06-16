package com.example.hrai.ai.service;

import com.example.hrai.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class AgentToolCallLoggerTest {

    private final AgentToolCallLogger logger = new AgentToolCallLogger();

    @Test
    void shouldLogToolCallLifecycleWithoutArgumentValues(CapturedOutput output) {
        AgentToolCallLogger.ToolCallLogContext context = logger.start(
                "session-123",
                "create_leave_pending",
                1,
                Map.of("message", "帮我请2026-06-23一天年假，原因是个人事务")
        );

        logger.success(context, ToolResult.success("trace-123", "pending created", Map.of()));

        assertThat(output).contains("event=mcp_agent_tool_call_start");
        assertThat(output).contains("event=mcp_agent_tool_call_finish");
        assertThat(output).contains("sessionId=session-123");
        assertThat(output).contains("toolName=create_leave_pending");
        assertThat(output).contains("sequence=1");
        assertThat(output).contains("status=SUCCESS");
        assertThat(output).contains("resultCode=SUCCESS");
        assertThat(output).contains("traceId=trace-123");
        assertThat(output).doesNotContain("个人事务");
    }
}
