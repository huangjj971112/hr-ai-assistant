package com.example.hrai.ai.tool.schema;

import com.example.hrai.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Tool 入参 JSON Schema 校验器。
 *
 * <p>项目暂不引入额外 JSON Schema 依赖，这里实现当前 schema 用到的核心约束：
 * required、additionalProperties、type、enum、format、pattern、长度和最小数值。
 * 它的定位是 LLM Tool Calling 的第一道参数闸门，业务权限仍由 Service 层继续兜底。</p>
 */
public class JsonSchemaToolArgumentValidator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void validate(String toolName, Map<String, Object> arguments) {
        String schema = ToolJsonSchemas.optionalSchemaFor(toolName)
                .orElseThrow(() -> new BusinessException("TOOL_SCHEMA_NOT_FOUND", "未配置 Tool JSON Schema：" + toolName));
        try {
            validateObject(toolName, objectMapper.readTree(schema), objectMapper.convertValue(arguments, JsonNode.class));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException | java.io.IOException exception) {
            throw new BusinessException("TOOL_SCHEMA_INVALID", "Tool JSON Schema 解析失败：" + toolName);
        }
    }

    private void validateObject(String toolName, JsonNode schema, JsonNode arguments) {
        if (!arguments.isObject()) {
            throw validationFailed(toolName, "$", "参数必须是 object");
        }
        JsonNode properties = schema.path("properties");
        validateRequired(toolName, schema, arguments);
        validateAdditionalProperties(toolName, schema, properties, arguments);
        Iterator<Map.Entry<String, JsonNode>> fields = arguments.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode fieldSchema = properties.get(field.getKey());
            if (fieldSchema != null) {
                validateField(toolName, field.getKey(), fieldSchema, field.getValue());
            }
        }
    }

    private void validateRequired(String toolName, JsonNode schema, JsonNode arguments) {
        for (JsonNode requiredField : schema.path("required")) {
            String fieldName = requiredField.asText();
            if (!arguments.has(fieldName) || arguments.get(fieldName).isNull()) {
                throw validationFailed(toolName, fieldName, "必填字段缺失");
            }
        }
    }

    private void validateAdditionalProperties(String toolName, JsonNode schema, JsonNode properties, JsonNode arguments) {
        if (!schema.path("additionalProperties").asBoolean(true)) {
            Iterator<String> names = arguments.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!properties.has(name)) {
                    throw validationFailed(toolName, name, "不允许的字段");
                }
            }
        }
    }

    private void validateField(String toolName, String fieldName, JsonNode schema, JsonNode value) {
        String type = schema.path("type").asText();
        if (!matchesType(type, value)) {
            throw validationFailed(toolName, fieldName, "类型必须是 " + type);
        }
        if (value.isTextual()) {
            validateString(toolName, fieldName, schema, value.asText());
        }
        if (value.isNumber() && schema.has("minimum") && value.asLong() < schema.get("minimum").asLong()) {
            throw validationFailed(toolName, fieldName, "必须大于等于 " + schema.get("minimum").asLong());
        }
    }

    private boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "boolean" -> value.isBoolean();
            case "object" -> value.isObject();
            default -> true;
        };
    }

    private void validateString(String toolName, String fieldName, JsonNode schema, String value) {
        if (schema.has("minLength") && value.length() < schema.get("minLength").asInt()) {
            throw validationFailed(toolName, fieldName, "长度不能小于 " + schema.get("minLength").asInt());
        }
        if (schema.has("maxLength") && value.length() > schema.get("maxLength").asInt()) {
            throw validationFailed(toolName, fieldName, "长度不能大于 " + schema.get("maxLength").asInt());
        }
        if (schema.has("enum") && !containsEnum(schema.get("enum"), value)) {
            throw validationFailed(toolName, fieldName, "枚举值非法：" + value);
        }
        if (schema.has("format")) {
            validateFormat(toolName, fieldName, schema.get("format").asText(), value);
        }
        if (schema.has("pattern") && !Pattern.compile(schema.get("pattern").asText()).matcher(value).matches()) {
            throw validationFailed(toolName, fieldName, "格式不符合 pattern");
        }
    }

    private boolean containsEnum(JsonNode enumValues, String value) {
        for (JsonNode enumValue : enumValues) {
            if (value.equals(enumValue.asText())) {
                return true;
            }
        }
        return false;
    }

    private void validateFormat(String toolName, String fieldName, String format, String value) {
        try {
            if ("date".equals(format)) {
                LocalDate.parse(value);
            }
            if ("date-time".equals(format)) {
                OffsetDateTime.parse(value);
            }
        } catch (DateTimeParseException exception) {
            throw validationFailed(toolName, fieldName, "必须是 ISO-8601 " + format);
        }
    }

    private BusinessException validationFailed(String toolName, String fieldName, String reason) {
        return new BusinessException(
                "TOOL_ARGUMENT_SCHEMA_VALIDATION_FAILED",
                "Tool 参数不符合 JSON Schema，tool=" + toolName + "，field=" + fieldName + "，原因：" + reason
        );
    }
}
