package com.example.hrai.ai.observation;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 单次工具调用的观测信息。
 *
 * @param toolName MCP Tool 名称，例如 query_leave_policy 或 query_attendance
 * @param status 工具调用结果状态
 * @param durationMs 工具调用耗时，单位毫秒
 * @param traceId MCP Tool 返回的追踪 ID，用于关联后端审计日志
 * @param inputSummary 工具输入的脱敏摘要，只允许展示安全字段
 * @param resultSummary 工具输出的脱敏摘要，只允许展示安全字段
 * @param evidenceSource 制度查询等工具返回的简短证据来源
 * @param errorCode 工具失败时的业务错误码；成功时通常为空
 */
public record AgentToolObservation(
        String toolName,
        AgentObservationStatus status,
        long durationMs,
        String traceId,
        Map<String, Object> inputSummary,
        Map<String, Object> resultSummary,
        String evidenceSource,
        String errorCode
) {

    /**
     * 统一空集合语义并隔离调用方的后续修改。
     *
     * <p>摘要只保留安全标量，避免把 Map/List/业务对象等复杂结构误展示到调试面板。</p>
     */
    public AgentToolObservation {
        Objects.requireNonNull(status, "status must not be null");
        inputSummary = copySafeSummary(inputSummary);
        resultSummary = copySafeSummary(resultSummary);
    }

    private static Map<String, Object> copySafeSummary(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            Object safeValue = safeScalar(value);
            if (key != null && safeValue != null) {
                safe.put(key, safeValue);
            }
        });
        return Map.copyOf(safe);
    }

    private static Object safeScalar(Object value) {
        if (value instanceof String
                || value instanceof Boolean
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigInteger
                || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toString();
        }
        if (value instanceof LocalTime time) {
            return time.toString();
        }
        return null;
    }
}
