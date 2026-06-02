package com.example.hrai.ai.service;

import com.example.hrai.ai.dify.DifyWorkflowClient;
import com.example.hrai.ai.dify.DifyWorkflowRequest;
import com.example.hrai.ai.dify.DifyWorkflowResponse;
import com.example.hrai.ai.dto.DifyWorkflowChatRequest;
import com.example.hrai.ai.dto.DifyWorkflowChatResponse;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DifyWorkflowChatService {

    private static final String DEMO_TENANT_ID = "demo-tenant";

    private final CurrentUserService currentUserService;
    private final HttpServletRequest servletRequest;
    private final DifyWorkflowClient difyWorkflowClient;

    public DifyWorkflowChatResponse chat(DifyWorkflowChatRequest request) {
        AuthenticatedUser user = currentUserService.currentUser();
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("message", request.getMessage());
        inputs.put("userId", user.userId());
        inputs.put("username", user.username());
        inputs.put("employeeName", user.employeeName());
        inputs.put("role", user.role().name());
        inputs.put("tenantId", DEMO_TENANT_ID);
        String token = currentBearerToken();
        if (StringUtils.hasText(token)) {
            inputs.put("token", token);
        }

        DifyWorkflowRequest workflowRequest = new DifyWorkflowRequest(inputs, "blocking", user.username());
        DifyWorkflowResponse workflowResponse = difyWorkflowClient.run(workflowRequest);
        return new DifyWorkflowChatResponse(
                workflowResponse.getAnswer(),
                workflowResponse.getSource(),
                workflowResponse.getRaw()
        );
    }

    private String currentBearerToken() {
        String authorization = servletRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }
}
