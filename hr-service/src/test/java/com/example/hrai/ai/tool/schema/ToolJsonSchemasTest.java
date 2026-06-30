package com.example.hrai.ai.tool.schema;

import com.example.hrai.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolJsonSchemasTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchemaToolArgumentValidator validator = new JsonSchemaToolArgumentValidator();

    @Test
    void shouldProvideDraft202012SchemaForEveryCurrentMcpTool() throws Exception {
        assertThat(ToolJsonSchemas.currentMcpToolNames()).containsExactlyInAnyOrder(
                "query_leave_balance",
                "query_attendance",
                "query_leave_policy",
                "create_leave_pending",
                "confirm_leave_apply",
                "cancel_pending"
        );

        for (String toolName : ToolJsonSchemas.currentMcpToolNames()) {
            JsonNode schema = objectMapper.readTree(ToolJsonSchemas.schemaFor(toolName));
            assertThat(schema.get("$schema").asText()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
            assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
            assertThat(schema.get("required")).isNotNull();
        }
    }

    @Test
    void shouldValidateCurrentToolArgumentsWithStrictAdditionalProperties() {
        validator.validate("query_attendance", Map.of(
                "toolToken", "token",
                "sessionId", "session-1",
                "startDate", "2026-06-01",
                "endDate", "2026-06-30"
        ));

        assertThatThrownBy(() -> validator.validate("query_attendance", Map.of(
                "toolToken", "token",
                "sessionId", "session-1",
                "startDate", "2026-06-01",
                "endDate", "2026-06-30",
                "employeeName", "李四"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许的字段");
    }

    @Test
    void shouldRejectNonIsoDateAndInvalidEnum() {
        assertThatThrownBy(() -> validator.validate("query_attendance", Map.of(
                "toolToken", "token",
                "sessionId", "session-1",
                "startDate", "2026年6月1日",
                "endDate", "2026-06-30"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须是 ISO-8601 date");

        assertThatThrownBy(() -> validator.validate("create_leave_apply_pending", Map.of(
                "employeeName", "张三",
                "leaveType", "年假",
                "startTime", "2026-07-01T09:00:00+08:00",
                "endTime", "2026-07-01T18:00:00+08:00",
                "reason", "个人事务"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("枚举值非法");
    }

    @Test
    void shouldKeepPlannedWorkflowSchemasTogetherWithCurrentMcpSchemas() {
        Set<String> toolNames = ToolJsonSchemas.allToolNames();

        assertThat(toolNames).contains(
                "query_salary",
                "query_overtime",
                "query_leave_records",
                "create_missing_card_pending",
                "create_comp_time_pending"
        );
    }
}
