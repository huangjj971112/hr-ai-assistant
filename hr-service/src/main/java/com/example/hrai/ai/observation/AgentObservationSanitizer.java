package com.example.hrai.ai.observation;

import com.example.hrai.ai.mcp.dto.PendingLeaveMcpResult;
import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.dto.attendance.AttendanceRecordResponse;
import com.example.hrai.dto.knowledge.KnowledgeAskResponse;
import com.example.hrai.dto.leave.LeaveApplyResponse;
import com.example.hrai.dto.leave.LeaveBalanceResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 将 MCP 工具输入输出收敛为可展示、可记录的脱敏摘要。
 *
 * <p>所有字段均按工具白名单提取，不递归复制任意业务对象或 Map。</p>
 */
public final class AgentObservationSanitizer {

    private static final int QUESTION_LIMIT = 80;
    private static final int POLICY_LIMIT = 120;
    private static final int SOURCE_LIMIT = 80;
    private static final int DATE_LIMIT = 32;
    private static final int VALUE_LIMIT = 80;
    private static final String DEFAULT_ERROR_CODE = "MCP_TOOL_CALL_FAILED";
    private static final String SAFE_ERROR_MESSAGE = "工具调用失败，请查看错误码";
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Set<String> SAFE_ERROR_CODES = Set.of(
            "VALIDATION_FAILED",
            "INVALID_TOOL_TOKEN",
            "TOOL_TOKEN_EXPIRED",
            "FORBIDDEN",
            "INVALID_SESSION_ID",
            "PENDING_NOT_FOUND",
            "PENDING_VERSION_CONFLICT",
            "IDEMPOTENCY_CONFLICT",
            "MCP_TOOL_CALL_FAILED",
            "MCP_CLIENT_CALL_FAILED",
            "MCP_REPEATED_TOOL_CALL",
            "MCP_TOOL_CALL_LIMIT_EXCEEDED"
    );

    /**
     * 按工具白名单提取安全输入摘要。
     */
    public Map<String, Object> sanitizeInput(String toolName, Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        switch (toolName == null ? "" : toolName) {
            case "query_attendance" -> {
                putDate(summary, "startDate", arguments.get("startDate"));
                putDate(summary, "endDate", arguments.get("endDate"));
            }
            case "query_leave_policy" -> putString(summary, "question", arguments.get("question"), QUESTION_LIMIT);
            case "create_leave_pending" -> {
                putDate(summary, "startDate", arguments.get("startDate"));
                putDate(summary, "endDate", arguments.get("endDate"));
                summary.put("request", "使用用户原始请假描述，原因已隐藏");
            }
            case "confirm_leave_apply", "cancel_pending" -> {
                putMaskedPendingId(summary, arguments.get("pendingId"));
                putNumber(summary, "expectedVersion", arguments.get("expectedVersion"));
            }
            default -> {
                return Map.of();
            }
        }
        return Map.copyOf(summary);
    }

    /**
     * 按六个 MCP 工具的固定白名单生成结果摘要。
     */
    public Map<String, Object> sanitizeResult(String toolName, ToolResult<?> result) {
        if (result == null) {
            return Map.of();
        }
        if (!result.success()) {
            return Map.of(
                    "errorCode", safeErrorCode(result.code()),
                    "errorMessage", SAFE_ERROR_MESSAGE
            );
        }

        return switch (toolName == null ? "" : toolName) {
            case "query_leave_balance" -> balanceSummary(result.data());
            case "query_attendance" -> attendanceSummary(result.data());
            case "query_leave_policy" -> policySummary(result.data());
            case "create_leave_pending", "cancel_pending" -> pendingSummary(result.data());
            case "confirm_leave_apply" -> confirmSummary(result.data());
            default -> Map.of();
        };
    }

    /**
     * 仅制度查询允许携带简短证据来源。
     */
    public String evidenceSource(String toolName, ToolResult<?> result) {
        if (!"query_leave_policy".equals(toolName) || result == null || !result.success()) {
            return null;
        }
        return textValue(result.data(), "source", SOURCE_LIMIT);
    }

    private Map<String, Object> balanceSummary(Object data) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (data instanceof LeaveBalanceResponse balance) {
            summary.put("annualLeaveBalance", balance.getAnnualLeaveBalance());
            summary.put("sickLeaveBalance", balance.getSickLeaveBalance());
        } else if (data instanceof Map<?, ?> map) {
            putNumber(summary, "annualLeaveBalance", map.get("annualLeaveBalance"));
            putNumber(summary, "sickLeaveBalance", map.get("sickLeaveBalance"));
        }
        return Map.copyOf(summary);
    }

    private Map<String, Object> attendanceSummary(Object data) {
        if (!(data instanceof List<?> records)) {
            return Map.of();
        }
        long lateCount = records.stream().filter(this::isLate).count();
        return Map.of("recordCount", records.size(), "lateCount", lateCount);
    }

    private boolean isLate(Object item) {
        if (item instanceof AttendanceRecordResponse record) {
            return "LATE".equalsIgnoreCase(record.status());
        }
        if (item instanceof Map<?, ?> map) {
            Object status = map.get("status");
            return status instanceof String value && "LATE".equalsIgnoreCase(value);
        }
        return false;
    }

    private Map<String, Object> policySummary(Object data) {
        Map<String, Object> summary = new LinkedHashMap<>();
        putSafeText(summary, "policySummary", textValue(data, "answer", POLICY_LIMIT));
        putSafeText(summary, "source", textValue(data, "source", SOURCE_LIMIT));
        return Map.copyOf(summary);
    }

    private Map<String, Object> pendingSummary(Object data) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (data instanceof PendingLeaveMcpResult pending) {
            putEnumOrString(summary, "leaveType", pending.leaveType(), VALUE_LIMIT);
            putTime(summary, "startTime", pending.startTime(), VALUE_LIMIT);
            putTime(summary, "endTime", pending.endTime(), VALUE_LIMIT);
            putEnumOrString(summary, "status", pending.status(), VALUE_LIMIT);
            putMaskedPendingId(summary, pending.pendingId());
        } else if (data instanceof Map<?, ?> map) {
            putEnumOrString(summary, "leaveType", map.get("leaveType"), VALUE_LIMIT);
            putTime(summary, "startTime", map.get("startTime"), VALUE_LIMIT);
            putTime(summary, "endTime", map.get("endTime"), VALUE_LIMIT);
            putEnumOrString(summary, "status", map.get("status"), VALUE_LIMIT);
            putMaskedPendingId(summary, map.get("pendingId"));
        }
        return Map.copyOf(summary);
    }

    private Map<String, Object> confirmSummary(Object data) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (data instanceof LeaveApplyResponse response) {
            putString(summary, "applyNo", response.getApplyNo(), VALUE_LIMIT);
            putEnumOrString(summary, "status", response.getStatus(), VALUE_LIMIT);
        } else if (data instanceof Map<?, ?> map) {
            putString(summary, "applyNo", map.get("applyNo"), VALUE_LIMIT);
            putEnumOrString(summary, "status", map.get("status"), VALUE_LIMIT);
        }
        return Map.copyOf(summary);
    }

    private String textValue(Object data, String field, int limit) {
        if (data instanceof Map<?, ?> map) {
            Object value = map.get(field);
            return value instanceof String text ? truncate(text, limit) : null;
        }
        if (data instanceof KnowledgeAskResponse response) {
            String value = switch (field) {
                case "answer" -> response.getAnswer();
                case "source" -> response.getSource();
                default -> null;
            };
            return truncate(value, limit);
        }
        return null;
    }

    private void putMaskedPendingId(Map<String, Object> target, Object pendingId) {
        if (!(pendingId instanceof String value) || value.isBlank()) {
            return;
        }
        value = truncate(value, VALUE_LIMIT);
        String masked = value.length() <= 4
                ? "****"
                : value.substring(0, Math.min(4, value.length())) + "***"
                + value.substring(Math.max(4, value.length() - 4));
        target.put("pendingId", masked);
    }

    private void putDate(Map<String, Object> target, String key, Object value) {
        if (value instanceof String text) {
            if (ISO_DATE.matcher(text).matches()) {
                putSafeText(target, key, truncate(text, DATE_LIMIT));
            }
        } else if (value instanceof LocalDate date) {
            target.put(key, date.toString());
        }
    }

    private void putNumber(Map<String, Object> target, String key, Object value) {
        if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long) {
            target.put(key, value);
        }
    }

    private void putString(Map<String, Object> target, String key, Object value, int limit) {
        if (value instanceof String text) {
            putSafeText(target, key, truncate(text, limit));
        }
    }

    private void putEnumOrString(Map<String, Object> target, String key, Object value, int limit) {
        if (value instanceof Enum<?> enumValue) {
            target.put(key, enumValue.name());
        } else {
            putString(target, key, value, limit);
        }
    }

    private void putTime(Map<String, Object> target, String key, Object value, int limit) {
        if (value instanceof LocalDate date) {
            putSafeText(target, key, truncate(date.toString(), limit));
        } else if (value instanceof LocalDateTime dateTime) {
            putSafeText(target, key, truncate(dateTime.toString(), limit));
        } else if (value instanceof LocalTime time) {
            putSafeText(target, key, truncate(time.toString(), limit));
        } else {
            putString(target, key, value, limit);
        }
    }

    private void putSafeText(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private String safeErrorCode(String code) {
        return SAFE_ERROR_CODES.contains(code) ? code : DEFAULT_ERROR_CODE;
    }

    private String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }
}
