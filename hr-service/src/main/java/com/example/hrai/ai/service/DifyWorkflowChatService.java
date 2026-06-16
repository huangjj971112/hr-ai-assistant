package com.example.hrai.ai.service;

import com.example.hrai.ai.dify.DifyWorkflowClient;
import com.example.hrai.ai.dify.DifyWorkflowRequest;
import com.example.hrai.ai.dify.DifyWorkflowResponse;
import com.example.hrai.ai.dto.AiAssistantType;
import com.example.hrai.ai.dto.DifyWorkflowChatRequest;
import com.example.hrai.ai.dto.DifyWorkflowChatResponse;
import com.example.hrai.ai.security.ToolTokenService;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class DifyWorkflowChatService {

    private static final String DEMO_TENANT_ID = "demo-tenant";
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ASSISTANT_TOOL_SCOPES = Set.of(
            "leave:balance:read",
            "leave:records:read",
            "attendance:records:read",
            "leave:policy:read",
            "leave:apply"
    );

    private final CurrentUserService currentUserService;
    private final ToolTokenService toolTokenService;
    private final DifyWorkflowClient difyWorkflowClient;
    private final ObjectMapper objectMapper;

    public DifyWorkflowChatResponse chat(DifyWorkflowChatRequest request) {
        AuthenticatedUser user = currentUserService.currentUser();
        String toolToken = toolTokenService.createToken(user, DEMO_TENANT_ID, ASSISTANT_TOOL_SCOPES);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("message", request.getMessage());
        inputs.put("query", request.getMessage());
        inputs.put("userId", user.userId());
        inputs.put("employeeId", user.userId());
        inputs.put("username", user.username());
        inputs.put("employeeName", user.employeeName());
        inputs.put("role", user.role().name());
        inputs.put("tenantId", DEMO_TENANT_ID);
        inputs.put("toolToken", toolToken);
        inputs.put("currentDateTime", LocalDateTime.now(BUSINESS_ZONE_ID).format(DATE_TIME_FORMATTER));

        DifyWorkflowRequest workflowRequest = new DifyWorkflowRequest(inputs, "blocking", user.username());
        DifyWorkflowResponse workflowResponse = request.getAgentType() == AiAssistantType.HR_AGENT
                ? difyWorkflowClient.runAgent(workflowRequest)
                : difyWorkflowClient.run(workflowRequest);
        JsonNode leaveDraft = findLeaveDraft(workflowResponse);
        if (leaveDraft != null) {
            return leaveDraftResponse(leaveDraft, workflowResponse.getSource());
        }

        DifyWorkflowChatResponse response = new DifyWorkflowChatResponse();
        response.setAnswer(toEmployeeAnswer(workflowResponse.getAnswer()));
        response.setSource(workflowResponse.getSource());
        return response;
    }

    private JsonNode findLeaveDraft(DifyWorkflowResponse workflowResponse) {
        JsonNode answerNode = parseJson(workflowResponse.getAnswer());
        if (isLeaveDraft(answerNode)) {
            return answerNode;
        }

        JsonNode outputs = objectMapper.valueToTree(workflowResponse.getRaw())
                .path("data")
                .path("outputs");
        if (isLeaveDraft(outputs)) {
            return outputs;
        }
        for (JsonNode value : outputs) {
            if (isLeaveDraft(value)) {
                return value;
            }
            if (value.isTextual()) {
                JsonNode parsed = parseJson(value.asText());
                if (isLeaveDraft(parsed)) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private JsonNode parseJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isLeaveDraft(JsonNode node) {
        return node != null
                && node.isObject()
                && node.hasNonNull("leaveType")
                && node.hasNonNull("startTime")
                && node.hasNonNull("endTime")
                && !node.path("submitted").asBoolean(false);
    }

    private DifyWorkflowChatResponse leaveDraftResponse(JsonNode draft, String source) {
        String message = draft.path("message").asText("请确认是否提交该请假申请。");
        String reason = draft.path("reason").asText();
        if (!StringUtils.hasText(reason)) {
            reason = "个人事务";
        }
        DifyWorkflowChatResponse response = new DifyWorkflowChatResponse();
        response.setAnswer(message);
        response.setSource(source);
        response.setNeedConfirm(true);
        response.setLeaveType(draft.path("leaveType").asText());
        response.setStartTime(draft.path("startTime").asText());
        response.setEndTime(draft.path("endTime").asText());
        response.setReason(reason);
        response.setMessage(message);
        return response;
    }

    private String toEmployeeAnswer(String answer) {
        if (!StringUtils.hasText(answer)) {
            return "流程已执行，但没有返回可展示的结果。";
        }
        try {
            JsonNode result = objectMapper.readTree(answer);
            if (result.isObject() && result.hasNonNull("annualLeaveBalance") && result.hasNonNull("sickLeaveBalance")) {
                return result.path("employeeName").asText("当前员工") + "当前年假余额 "
                        + result.path("annualLeaveBalance").asText() + " 天，病假余额 "
                        + result.path("sickLeaveBalance").asText() + " 天。";
            }
            if (result.isObject() && result.has("submitted") && result.hasNonNull("leaveType")) {
                return formatLeaveApplyResult(result);
            }
            if (result.isArray()) {
                return formatRecordList(result);
            }
        } catch (Exception ignored) {
            return answer;
        }
        return answer;
    }

    private String formatLeaveApplyResult(JsonNode result) {
        String summary = "请假类型：" + leaveTypeText(result.path("leaveType").asText())
                + "，时间：" + result.path("startTime").asText()
                + " 至 " + result.path("endTime").asText()
                + "，原因：" + result.path("reason").asText();
        if (result.path("submitted").asBoolean(false)) {
            return "请假申请已提交，申请编号：" + result.path("applyNo").asText()
                    + "，状态：" + result.path("status").asText() + "。\n" + summary;
        }
        return "已生成请假申请草稿，请确认后再提交。\n" + summary;
    }

    private String formatRecordList(JsonNode records) {
        if (records.isEmpty()) {
            return "没有查询到相关记录。";
        }
        JsonNode first = records.get(0);
        if (first.has("attendanceDate")) {
            String details = StreamSupport.stream(records.spliterator(), false)
                    .map(record -> record.path("attendanceDate").asText() + "："
                            + attendanceStatusText(record.path("status").asText())
                            + "，签到 " + record.path("checkInTime").asText("-")
                            + "，签退 " + record.path("checkOutTime").asText("-")
                            + "，" + record.path("remark").asText(""))
                    .collect(Collectors.joining("\n"));
            return "查询到 " + records.size() + " 条考勤记录：\n" + details;
        }
        if (first.has("applyNo")) {
            String details = StreamSupport.stream(records.spliterator(), false)
                    .map(record -> record.path("applyNo").asText() + "："
                            + leaveTypeText(record.path("leaveType").asText())
                            + "，" + record.path("startTime").asText()
                            + " 至 " + record.path("endTime").asText()
                            + "，状态 " + record.path("status").asText())
                    .collect(Collectors.joining("\n"));
            return "查询到 " + records.size() + " 条请假记录：\n" + details;
        }
        return "查询到 " + records.size() + " 条记录。";
    }

    private String attendanceStatusText(String status) {
        return switch (status) {
            case "NORMAL" -> "正常";
            case "LATE" -> "迟到";
            case "EARLY_LEAVE" -> "早退";
            case "ABSENT" -> "缺勤";
            default -> status;
        };
    }

    private String leaveTypeText(String leaveType) {
        return switch (leaveType) {
            case "ANNUAL" -> "年假";
            case "SICK" -> "病假";
            case "PERSONAL" -> "事假";
            default -> leaveType;
        };
    }
}
