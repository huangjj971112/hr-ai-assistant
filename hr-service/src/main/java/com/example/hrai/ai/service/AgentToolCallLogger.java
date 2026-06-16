package com.example.hrai.ai.service;

import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

final class AgentToolCallLogger {

    private static final Logger log = LoggerFactory.getLogger(AgentToolCallLogger.class);

    ToolCallLogContext start(String sessionId, String toolName, int sequence, Map<String, Object> modelArguments) {
        ToolCallLogContext context = new ToolCallLogContext(sessionId, toolName, sequence, System.nanoTime());
        log.info("event=mcp_agent_tool_call_start sessionId={} toolName={} sequence={} argumentKeys={}",
                sessionId, toolName, sequence, modelArguments.keySet());
        return context;
    }

    void success(ToolCallLogContext context, ToolResult<?> result) {
        log.info("event=mcp_agent_tool_call_finish sessionId={} toolName={} sequence={} status={} resultCode={} traceId={} durationMs={}",
                context.sessionId(), context.toolName(), context.sequence(), result.success() ? "SUCCESS" : "FAILED",
                result.code(), result.traceId(), context.durationMs());
    }

    void failure(ToolCallLogContext context, RuntimeException exception) {
        String code = exception instanceof BusinessException businessException
                ? businessException.getCode()
                : exception.getClass().getSimpleName();
        log.warn("event=mcp_agent_tool_call_finish sessionId={} toolName={} sequence={} status=EXCEPTION resultCode={} durationMs={}",
                context.sessionId(), context.toolName(), context.sequence(), code, context.durationMs());
    }

    record ToolCallLogContext(String sessionId, String toolName, int sequence, long startedAtNanos) {

        long durationMs() {
            return Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000);
        }
    }
}
