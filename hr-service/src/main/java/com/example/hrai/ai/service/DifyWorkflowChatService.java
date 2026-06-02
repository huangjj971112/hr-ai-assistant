package com.example.hrai.ai.service;

import com.example.hrai.ai.dify.DifyWorkflowClient;
import com.example.hrai.ai.dify.DifyWorkflowRequest;
import com.example.hrai.ai.dify.DifyWorkflowResponse;
import com.example.hrai.ai.dto.DifyWorkflowChatRequest;
import com.example.hrai.ai.dto.DifyWorkflowChatResponse;
import com.example.hrai.ai.security.ToolTokenService;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DifyWorkflowChatService {

    private static final String DEMO_TENANT_ID = "demo-tenant";
    private static final Set<String> WORKFLOW_TOOL_SCOPES = Set.of(
            "leave:balance:read",
            "leave:records:read"
    );

    private final CurrentUserService currentUserService;
    private final ToolTokenService toolTokenService;
    private final DifyWorkflowClient difyWorkflowClient;
    private final ObjectMapper objectMapper;

    public DifyWorkflowChatResponse chat(DifyWorkflowChatRequest request) {
        AuthenticatedUser user = currentUserService.currentUser();
        String toolToken = toolTokenService.createToken(user, DEMO_TENANT_ID, WORKFLOW_TOOL_SCOPES);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("message", request.getMessage());
        inputs.put("userId", user.userId());
        inputs.put("username", user.username());
        inputs.put("employeeName", user.employeeName());
        inputs.put("role", user.role().name());
        inputs.put("tenantId", DEMO_TENANT_ID);
        inputs.put("toolToken", toolToken);

        DifyWorkflowRequest workflowRequest = new DifyWorkflowRequest(inputs, "blocking", user.username());
        DifyWorkflowResponse workflowResponse = difyWorkflowClient.run(workflowRequest);
        return new DifyWorkflowChatResponse(
                toEmployeeAnswer(workflowResponse.getAnswer()),
                workflowResponse.getSource()
        );
    }

    private String toEmployeeAnswer(String answer) {
        if (!StringUtils.hasText(answer)) {
            return "流程已执行，但没有返回可展示的结果。";
        }
        try {
            Map<String, Object> result = objectMapper.readValue(answer, new TypeReference<>() {
            });
            Object employeeName = result.get("employeeName");
            Object annualLeaveBalance = result.get("annualLeaveBalance");
            Object sickLeaveBalance = result.get("sickLeaveBalance");
            if (employeeName != null && annualLeaveBalance != null && sickLeaveBalance != null) {
                return employeeName + "当前年假余额 " + annualLeaveBalance + " 天，病假余额 "
                        + sickLeaveBalance + " 天。";
            }
        } catch (Exception ignored) {
            return answer;
        }
        return answer;
    }
}
