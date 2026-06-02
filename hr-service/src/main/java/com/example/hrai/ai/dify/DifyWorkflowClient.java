package com.example.hrai.ai.dify;

import com.example.hrai.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DifyWorkflowClient {

    private final DifyWorkflowProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public DifyWorkflowClient(DifyWorkflowProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeoutSeconds() * 1000);
        requestFactory.setReadTimeout(properties.getTimeoutSeconds() * 1000);
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public boolean isReady() {
        return properties.isEnabled() && StringUtils.hasText(properties.getApiKey());
    }

    public DifyWorkflowResponse run(DifyWorkflowRequest request) {
        if (!isReady()) {
            return mockResponse(request);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", request.getInputs());
        body.put("response_mode", request.getResponseMode());
        body.put("user", request.getUser());

        try {
            Map<String, Object> responseBody = restClient.post()
                    .uri(properties.getWorkflowPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(properties.getApiKey()))
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (responseBody == null) {
                throw new BusinessException("DIFY_WORKFLOW_EMPTY_RESPONSE", "Dify Workflow 返回为空");
            }
            return new DifyWorkflowResponse(extractAnswer(responseBody), "dify-workflow", responseBody);
        } catch (RestClientException exception) {
            throw new BusinessException("DIFY_WORKFLOW_REQUEST_FAILED", "调用 Dify Workflow 失败：" + exception.getMessage());
        }
    }

    private DifyWorkflowResponse mockResponse(DifyWorkflowRequest request) {
        Map<String, Object> inputs = request.getInputs() == null ? Map.of() : request.getInputs();
        Object message = inputs.getOrDefault("message", "");
        Object employeeName = inputs.getOrDefault("employeeName", inputs.getOrDefault("username", "当前用户"));
        String answer = "Dify Workflow 未启用，当前返回本地 mock。已收到问题：" + message
                + "。如果这是年假余额查询，请在 Dify Workflow 的 HTTP 节点调用 /api/ai/tools/leave/balance，员工上下文：" + employeeName + "。";
        return new DifyWorkflowResponse(answer, "mock-dify-workflow", Map.of("inputs", inputs));
    }

    private String extractAnswer(Map<String, Object> responseBody) {
        Object directAnswer = responseBody.get("answer");
        if (directAnswer instanceof String answer && StringUtils.hasText(answer)) {
            return answer;
        }

        Map<String, Object> data = asMap(responseBody.get("data"));
        Map<String, Object> outputs = asMap(data.get("outputs"));
        for (String key : new String[]{"answer", "text", "result", "output"}) {
            Object value = outputs.get(key);
            if (value instanceof String text && StringUtils.hasText(text)) {
                return text;
            }
        }
        Object firstStringOutput = outputs.values().stream()
                .filter(String.class::isInstance)
                .findFirst()
                .orElse(null);
        if (firstStringOutput != null) {
            return String.valueOf(firstStringOutput);
        }
        Object error = data.get("error");
        if (error != null && StringUtils.hasText(String.valueOf(error))) {
            throw new BusinessException("DIFY_WORKFLOW_FAILED", "Dify Workflow 执行失败：" + error);
        }
        return "Dify Workflow 已执行，但未找到 answer 输出。原始返回：" + responseBody;
    }

    private Map<String, Object> asMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return objectMapper.convertValue(value, new TypeReference<>() {
        });
    }
}
