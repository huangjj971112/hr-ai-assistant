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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
            return mockResponse(request, "mock-dify-workflow");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", request.getInputs());
        body.put("response_mode", request.getResponseMode());
        body.put("user", request.getUser());

        return post(
                properties.getWorkflowPath(),
                properties.getApiKey(),
                body,
                "DIFY_WORKFLOW_EMPTY_RESPONSE",
                "Dify Workflow 返回为空",
                "DIFY_WORKFLOW_REQUEST_FAILED",
                "调用 Dify Workflow 失败：",
                "dify-workflow"
        );
    }

    public DifyWorkflowResponse runAgent(DifyWorkflowRequest request) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getAgentApiKey())) {
            return mockResponse(request, "mock-dify-agent");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", request.getInputs());
        body.put("query", request.getInputs().getOrDefault("query", ""));
        body.put("response_mode", "streaming");
        body.put("user", request.getUser());

        return postStreamingAgent(body);
    }

    private DifyWorkflowResponse postStreamingAgent(Map<String, Object> body) {
        try {
            return restClient.post()
                    .uri(properties.getAgentPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(properties.getAgentApiKey()))
                    .body(body)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new BusinessException(
                                    "DIFY_AGENT_REQUEST_FAILED",
                                    "调用 Dify Agent 失败：" + response.getStatusCode()
                            );
                        }

                        StringBuilder answer = new StringBuilder();
                        List<Map<String, Object>> events = new ArrayList<>();
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8)
                        )) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                handleAgentStreamLine(line, answer, events);
                            }
                        }
                        if (answer.isEmpty()) {
                            throw new BusinessException("DIFY_AGENT_EMPTY_RESPONSE", "Dify Agent 返回为空");
                        }
                        return new DifyWorkflowResponse(
                                answer.toString(),
                                "dify-agent",
                                Map.of("events", events)
                        );
                    });
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new BusinessException("DIFY_AGENT_REQUEST_FAILED", "调用 Dify Agent 失败：" + exception.getMessage());
        }
    }

    private void handleAgentStreamLine(
            String line,
            StringBuilder answer,
            List<Map<String, Object>> events
    ) throws IOException {
        if (!line.startsWith("data:")) {
            return;
        }
        String payload = line.substring("data:".length()).trim();
        if (!StringUtils.hasText(payload) || "[DONE]".equals(payload)) {
            return;
        }

        Map<String, Object> event = objectMapper.readValue(payload, new TypeReference<>() {
        });
        events.add(event);
        if ("error".equals(event.get("event"))) {
            throw new BusinessException(
                    "DIFY_AGENT_FAILED",
                    "Dify Agent 执行失败：" + event.getOrDefault("message", payload)
            );
        }
        Object answerValue = event.get("answer");
        if (answerValue != null && StringUtils.hasText(String.valueOf(answerValue))) {
            answer.append(answerValue);
        }
    }

    private DifyWorkflowResponse post(
            String path,
            String apiKey,
            Map<String, Object> body,
            String emptyResponseCode,
            String emptyResponseMessage,
            String requestFailedCode,
            String requestFailedMessage,
            String source
    ) {
        try {
            Map<String, Object> responseBody = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (responseBody == null) {
                throw new BusinessException(emptyResponseCode, emptyResponseMessage);
            }
            return new DifyWorkflowResponse(extractAnswer(responseBody), source, responseBody);
        } catch (RestClientException exception) {
            throw new BusinessException(requestFailedCode, requestFailedMessage + exception.getMessage());
        }
    }

    private DifyWorkflowResponse mockResponse(DifyWorkflowRequest request, String source) {
        Map<String, Object> inputs = request.getInputs() == null ? Map.of() : request.getInputs();
        Object message = inputs.getOrDefault("message", "");
        Object employeeName = inputs.getOrDefault("employeeName", inputs.getOrDefault("username", "当前用户"));
        String assistantName = source.equals("mock-dify-agent") ? "Dify HR Agent" : "Dify Workflow";
        String answer = assistantName + " 未启用，当前返回本地 mock。已收到问题：" + message
                + "，员工上下文：" + employeeName + "。";
        return new DifyWorkflowResponse(answer, source, Map.of("inputs", inputs));
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
