package com.example.hrai.ai.tool;

/**
 * MCP Tool 的统一返回结构。
 *
 * @param traceId 一次 Tool 调用的追踪标识，可用于关联接口响应与审计日志
 */
public record ToolResult<T>(
        boolean success,
        String code,
        String message,
        String traceId,
        T data
) {

    public static <T> ToolResult<T> success(String message, T data) {
        return success(java.util.UUID.randomUUID().toString(), message, data);
    }

    public static <T> ToolResult<T> success(String traceId, String message, T data) {
        return new ToolResult<>(true, "SUCCESS", message, traceId, data);
    }

    public static <T> ToolResult<T> failure(String code, String message) {
        return failure(java.util.UUID.randomUUID().toString(), code, message);
    }

    public static <T> ToolResult<T> failure(String traceId, String code, String message) {
        return new ToolResult<>(false, code, message, traceId, null);
    }
}
