package com.example.hrai.ai.tool.schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * LLM Tool Calling 参数 JSON Schema 注册表。
 *
 * <p>这里集中维护所有“LLM/Agent -> Tool”边界的入参约束：
 * 1. 当前 MCP 已实现工具会在调用 MCP 前参与校验；
 * 2. 工资、加班、补卡、调休等暂未落地的工具先保留 schema，后续新增 Tool 时直接复用。</p>
 */
public final class ToolJsonSchemas {

    private static final String SCHEMA_URI = "https://json-schema.org/draft/2020-12/schema";
    private static final Map<String, String> SCHEMAS = buildSchemas();
    private static final Set<String> CURRENT_MCP_TOOL_NAMES = Set.of(
            "query_leave_balance",
            "query_attendance",
            "query_leave_policy",
            "create_leave_pending",
            "confirm_leave_apply",
            "cancel_pending"
    );

    private ToolJsonSchemas() {
    }

    public static String schemaFor(String toolName) {
        return optionalSchemaFor(toolName)
                .orElseThrow(() -> new IllegalArgumentException("No JSON Schema registered for tool: " + toolName));
    }

    public static Optional<String> optionalSchemaFor(String toolName) {
        return Optional.ofNullable(SCHEMAS.get(toolName));
    }

    public static Set<String> allToolNames() {
        return Collections.unmodifiableSet(SCHEMAS.keySet());
    }

    public static Set<String> currentMcpToolNames() {
        return CURRENT_MCP_TOOL_NAMES;
    }

    public static Map<String, String> allSchemas() {
        return Collections.unmodifiableMap(SCHEMAS);
    }

    private static Map<String, String> buildSchemas() {
        Map<String, String> schemas = new LinkedHashMap<>();
        schemas.put("query_leave_balance", """
                {
                  "$schema": "%s",
                  "title": "query_leave_balance",
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["toolToken"],
                  "properties": {
                    "toolToken": { "type": "string", "minLength": 1, "maxLength": 2048 },
                    "sessionId": { "type": "string", "minLength": 1, "maxLength": 128 }
                  }
                }
                """.formatted(SCHEMA_URI));
        schemas.put("query_attendance", """
                {
                  "$schema": "%s",
                  "title": "query_attendance",
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["toolToken", "startDate", "endDate"],
                  "properties": {
                    "toolToken": { "type": "string", "minLength": 1, "maxLength": 2048 },
                    "sessionId": { "type": "string", "minLength": 1, "maxLength": 128 },
                    "startDate": { "type": "string", "format": "date" },
                    "endDate": { "type": "string", "format": "date" },
                    "status": { "type": "string", "enum": ["ALL", "NORMAL", "LATE", "EARLY_LEAVE", "ABSENT", "MISSING_CARD"] }
                  }
                }
                """.formatted(SCHEMA_URI));
        schemas.put("query_leave_policy", """
                {
                  "$schema": "%s",
                  "title": "query_leave_policy",
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["toolToken", "question"],
                  "properties": {
                    "toolToken": { "type": "string", "minLength": 1, "maxLength": 2048 },
                    "sessionId": { "type": "string", "minLength": 1, "maxLength": 128 },
                    "question": { "type": "string", "minLength": 1, "maxLength": 500 },
                    "policyType": {
                      "type": "string",
                      "enum": ["LEAVE", "ATTENDANCE", "SALARY", "OVERTIME", "HANDBOOK", "GENERAL_RULE"]
                    }
                  }
                }
                """.formatted(SCHEMA_URI));
        schemas.put("create_leave_pending", """
                {
                  "$schema": "%s",
                  "title": "create_leave_pending",
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["toolToken", "sessionId", "message"],
                  "properties": {
                    "toolToken": { "type": "string", "minLength": 1, "maxLength": 2048 },
                    "sessionId": { "type": "string", "minLength": 1, "maxLength": 128 },
                    "message": { "type": "string", "minLength": 1, "maxLength": 1000 }
                  }
                }
                """.formatted(SCHEMA_URI));
        schemas.put("confirm_leave_apply", """
                {
                  "$schema": "%s",
                  "title": "confirm_leave_apply",
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["toolToken", "sessionId", "pendingId", "expectedVersion", "idempotencyKey"],
                  "properties": {
                    "toolToken": { "type": "string", "minLength": 1, "maxLength": 2048 },
                    "sessionId": { "type": "string", "minLength": 1, "maxLength": 128 },
                    "pendingId": { "type": "string", "minLength": 1, "maxLength": 64 },
                    "expectedVersion": { "type": "integer", "minimum": 1 },
                    "idempotencyKey": { "type": "string", "minLength": 1, "maxLength": 128 }
                  }
                }
                """.formatted(SCHEMA_URI));
        schemas.put("cancel_pending", """
                {
                  "$schema": "%s",
                  "title": "cancel_pending",
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["toolToken", "sessionId", "pendingId", "expectedVersion"],
                  "properties": {
                    "toolToken": { "type": "string", "minLength": 1, "maxLength": 2048 },
                    "sessionId": { "type": "string", "minLength": 1, "maxLength": 128 },
                    "pendingId": { "type": "string", "minLength": 1, "maxLength": 64 },
                    "expectedVersion": { "type": "integer", "minimum": 1 }
                  }
                }
                """.formatted(SCHEMA_URI));

        schemas.put("query_leave_records", """
                {
                  "$schema": "%s",
                  "title": "query_leave_records",
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["employeeName", "startDate", "endDate"],
                  "properties": {
                    "employeeName": { "type": "string", "minLength": 1, "maxLength": 64 },
                    "startDate": { "type": "string", "format": "date" },
                    "endDate": { "type": "string", "format": "date" },
                    "leaveType": { "type": "string", "enum": ["ANNUAL", "SICK", "PERSONAL", "ALL"] },
                    "status": { "type": "string", "enum": ["ALL", "PENDING", "APPROVED", "REJECTED", "CANCELLED"] }
                  }
                }
                """.formatted(SCHEMA_URI));
        schemas.put("query_salary", """
                {
                  "$schema": "%s",
                  "title": "query_salary",
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["toolToken", "salaryMonth"],
                  "properties": {
                    "toolToken": { "type": "string", "minLength": 1 },
                    "salaryMonth": { "type": "string", "pattern": "^\\\\d{4}-(0[1-9]|1[0-2])$" }
                  }
                }
                """.formatted(SCHEMA_URI));
        schemas.put("query_overtime", """
                {
                  "$schema": "%s",
                  "title": "query_overtime",
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["employeeName", "startDate", "endDate"],
                  "properties": {
                    "employeeName": { "type": "string", "minLength": 1, "maxLength": 64 },
                    "startDate": { "type": "string", "format": "date" },
                    "endDate": { "type": "string", "format": "date" },
                    "status": { "type": "string", "enum": ["ALL", "PENDING", "APPROVED", "REJECTED"] }
                  }
                }
                """.formatted(SCHEMA_URI));
        schemas.put("create_leave_apply_pending", """
                {
                  "$schema": "%s",
                  "title": "create_leave_apply_pending",
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["employeeName", "leaveType", "startTime", "endTime", "reason"],
                  "properties": {
                    "employeeName": { "type": "string", "minLength": 1, "maxLength": 64 },
                    "leaveType": { "type": "string", "enum": ["ANNUAL", "SICK", "PERSONAL"] },
                    "startTime": { "type": "string", "format": "date-time" },
                    "endTime": { "type": "string", "format": "date-time" },
                    "reason": { "type": "string", "minLength": 1, "maxLength": 500 }
                  }
                }
                """.formatted(SCHEMA_URI));
        schemas.put("create_missing_card_pending", """
                {
                  "$schema": "%s",
                  "title": "create_missing_card_pending",
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["employeeName", "attendanceDate", "cardType", "actualTime", "reason"],
                  "properties": {
                    "employeeName": { "type": "string", "minLength": 1, "maxLength": 64 },
                    "attendanceDate": { "type": "string", "format": "date" },
                    "cardType": { "type": "string", "enum": ["CHECK_IN", "CHECK_OUT"] },
                    "actualTime": { "type": "string", "format": "date-time" },
                    "reason": { "type": "string", "minLength": 1, "maxLength": 500 }
                  }
                }
                """.formatted(SCHEMA_URI));
        schemas.put("create_comp_time_pending", """
                {
                  "$schema": "%s",
                  "title": "create_comp_time_pending",
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["employeeName", "startTime", "endTime", "reason"],
                  "properties": {
                    "employeeName": { "type": "string", "minLength": 1, "maxLength": 64 },
                    "startTime": { "type": "string", "format": "date-time" },
                    "endTime": { "type": "string", "format": "date-time" },
                    "reason": { "type": "string", "minLength": 1, "maxLength": 500 }
                  }
                }
                """.formatted(SCHEMA_URI));
        return schemas;
    }
}
