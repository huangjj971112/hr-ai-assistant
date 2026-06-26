package com.example.hrai.service.dify;

import com.example.hrai.config.DifyProperties;
import com.example.hrai.dto.knowledge.KnowledgeAskResponse;
import com.example.hrai.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class DifyChatClient {

    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DifyProperties difyProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public DifyChatClient(DifyProperties difyProperties, ObjectMapper objectMapper) {
        this.difyProperties = difyProperties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(difyProperties.getTimeoutSeconds() * 1000);
        requestFactory.setReadTimeout(difyProperties.getTimeoutSeconds() * 1000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public boolean isReady() {
        return difyProperties.isEnabled()
                && (StringUtils.hasText(difyProperties.getApiKey())
                || StringUtils.hasText(difyProperties.getAgentApiKey()));
    }

    public KnowledgeAskResponse ask(String question, String user) {
        return ask(question, user, Map.of());
    }

    public KnowledgeAskResponse ask(String question, String user, Map<String, Object> extraInputs) {
        if (!isReady()) {
            return fallback(question);
        }

        if (!StringUtils.hasText(difyProperties.getApiKey())) {
            return askAgentStreaming(question, user, extraInputs);
        }

        try {
            return askWorkflow(question, user, extraInputs);
        } catch (HttpStatusCodeException exception) {
            if (!isNotWorkflowApp(exception)) {
                throw new BusinessException("DIFY_WORKFLOW_REQUEST_FAILED",
                        "调用 Dify Workflow 失败：" + exception.getMessage());
            }
            return askChatBlocking(question, user, extraInputs);
        } catch (RestClientException exception) {
            throw new BusinessException("DIFY_WORKFLOW_REQUEST_FAILED",
                    "调用 Dify Workflow 失败：" + exception.getMessage());
        }
    }

    private KnowledgeAskResponse askWorkflow(String question, String user, Map<String, Object> extraInputs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", difyInputs(question, user, extraInputs));
        body.put("response_mode", "blocking");
        body.put("user", user);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(difyProperties.getApiKey());
        ResponseEntity<Map> response = restTemplate.exchange(
                difyProperties.getBaseUrl() + difyProperties.getWorkflowPath(),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );
        Map<?, ?> responseBody = response.getBody();
        if (responseBody == null) {
            throw new BusinessException("DIFY_WORKFLOW_EMPTY_RESPONSE", "Dify Workflow 返回为空");
        }
        Map<?, ?> outputs = workflowOutputs(responseBody);
        // 推荐 Workflow 输出 answer/source；output 仅用于兼容早期单字段结束节点。
        String answer = firstText(outputs, "answer", "text", "result", "output");
        if (!StringUtils.hasText(answer)) {
            throw new BusinessException("DIFY_WORKFLOW_EMPTY_RESPONSE", "Dify Workflow 未返回 answer");
        }
        String source = firstText(outputs, "source", "document", "dataset", "knowledgeSource");
        if (!StringUtils.hasText(source)) {
            source = "dify-workflow-handbook";
        }
        String workflowRunId = responseBody.get("workflow_run_id") == null
                ? null
                : String.valueOf(responseBody.get("workflow_run_id"));
        return new KnowledgeAskResponse(answer, source, workflowRunId);
    }

    private KnowledgeAskResponse askChatBlocking(String question, String user, Map<String, Object> extraInputs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", difyInputs(question, user, extraInputs));
        body.put("query", question);
        body.put("response_mode", "blocking");
        body.put("user", user);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(difyProperties.getApiKey());
            ResponseEntity<Map> response = restTemplate.exchange(
                    difyProperties.getBaseUrl() + "/chat-messages",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null) {
                throw new BusinessException("DIFY_EMPTY_RESPONSE", "Dify 返回为空");
            }
            Object answerValue = responseBody.get("answer");
            String answer = answerValue == null ? "" : String.valueOf(answerValue);
            String conversationId = responseBody.get("conversation_id") == null
                    ? null
                    : String.valueOf(responseBody.get("conversation_id"));
            return new KnowledgeAskResponse(answer, "dify-employee-handbook", conversationId);
        } catch (HttpStatusCodeException exception) {
            if (isNotChatApp(exception) && StringUtils.hasText(difyProperties.getAgentApiKey())) {
                return askAgentStreaming(question, user, extraInputs);
            }
            throw new BusinessException("DIFY_REQUEST_FAILED", "调用 Dify 失败：" + exception.getMessage());
        } catch (RestClientException exception) {
            throw new BusinessException("DIFY_REQUEST_FAILED", "调用 Dify 失败：" + exception.getMessage());
        }
    }

    private Map<?, ?> workflowOutputs(Map<?, ?> responseBody) {
        Object data = responseBody.get("data");
        if (data instanceof Map<?, ?> dataMap && dataMap.get("outputs") instanceof Map<?, ?> outputs) {
            return outputs;
        }
        Object outputs = responseBody.get("outputs");
        if (outputs instanceof Map<?, ?> outputMap) {
            return outputMap;
        }
        return Map.of();
    }

    private String firstText(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private KnowledgeAskResponse askAgentStreaming(String question, String user, Map<String, Object> extraInputs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", difyInputs(question, user, extraInputs));
        body.put("query", question);
        body.put("response_mode", "streaming");
        body.put("user", user);

        RequestCallback requestCallback = request -> {
            request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            request.getHeaders().setBearerAuth(difyProperties.getAgentApiKey());
            objectMapper.writeValue(request.getBody(), body);
        };
        ResponseExtractor<KnowledgeAskResponse> responseExtractor = response -> {
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException("DIFY_AGENT_REQUEST_FAILED",
                        "调用 Dify Agent 失败：" + response.getStatusCode());
            }
            StringBuilder answer = new StringBuilder();
            String[] conversationId = new String[1];
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleAgentStreamLine(line, answer, conversationId);
                }
            }
            if (answer.isEmpty()) {
                throw new BusinessException("DIFY_AGENT_EMPTY_RESPONSE", "Dify Agent 返回为空");
            }
            return new KnowledgeAskResponse(answer.toString(), "dify-agent-handbook", conversationId[0]);
        };

        try {
            return restTemplate.execute(
                    difyProperties.getBaseUrl() + difyProperties.getAgentPath(),
                    HttpMethod.POST,
                    requestCallback,
                    responseExtractor
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new BusinessException("DIFY_AGENT_REQUEST_FAILED", "调用 Dify Agent 失败：" + exception.getMessage());
        }
    }

    private boolean isNotChatApp(HttpStatusCodeException exception) {
        return exception.getResponseBodyAsString().contains("\"not_chat_app\"");
    }

    private boolean isNotWorkflowApp(HttpStatusCodeException exception) {
        return exception.getStatusCode().value() == 404
                || exception.getResponseBodyAsString().contains("\"not_workflow_app\"");
    }

    private Map<String, Object> difyInputs(String question, String employeeName, Map<String, Object> extraInputs) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("message", question);
        inputs.put("query", question);
        inputs.put("employeeName", employeeName);
        inputs.put("currentDateTime", LocalDateTime.now(BUSINESS_ZONE_ID).format(DATE_TIME_FORMATTER));
        if (extraInputs != null) {
            inputs.putAll(extraInputs);
        }
        return inputs;
    }

    private void handleAgentStreamLine(String line, StringBuilder answer, String[] conversationId) throws IOException {
        if (!line.startsWith("data:")) {
            return;
        }
        String payload = line.substring("data:".length()).trim();
        if (!StringUtils.hasText(payload) || "[DONE]".equals(payload)) {
            return;
        }

        Map<String, Object> event = objectMapper.readValue(payload, new TypeReference<>() {
        });
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
        Object eventConversationId = event.get("conversation_id");
        if (eventConversationId != null) {
            conversationId[0] = String.valueOf(eventConversationId);
        }
    }

    public SseEmitter streamAsk(String question, String user) {
        SseEmitter emitter = new SseEmitter(difyProperties.getTimeoutSeconds() * 1000L);
        CompletableFuture.runAsync(() -> {
            try {
                if (!isReady()) {
                    streamFallback(question, emitter);
                    return;
                }
                streamFromDify(question, user, emitter);
            } catch (Exception exception) {
                sendError(emitter, exception);
            }
        });
        return emitter;
    }

    private KnowledgeAskResponse fallback(String question) {
        /*
         * 本地演示环境通常不会配置 Dify。这里提供一小段可验证的“模拟员工手册”，
         * 让 Multi-Agent 在没有真实知识库时也能拿到制度依据；一旦配置 Dify，
         * ask(...) 会直接调用真实知识库，不会使用这些 mock 规则。
         */
        String answer = "员工手册智能问答尚未连接 Dify。"
                + localPolicyAnswer(question)
                + "请配置 DIFY_API_KEY，并在 Dify 应用知识库中上传员工手册后重试。当前问题：" + question;
        return new KnowledgeAskResponse(answer, "mock-employee-handbook", null);
    }

    private String localPolicyAnswer(String question) {
        if (!StringUtils.hasText(question)) {
            return "";
        }
        if (question.contains("病假")) {
            return "本地模拟制度：病假工资按公司病假制度执行，可能根据病假天数和证明材料按比例发放，存在扣款或工资影响；具体金额需由 HR 按薪酬规则确认。";
        }
        if (question.contains("迟到") || question.contains("考勤")) {
            return "本地模拟制度：迟到记录可能按照考勤制度影响绩效或产生扣款，具体以当月考勤汇总和 HR 审核为准。";
        }
        if (question.contains("年假")) {
            return "本地模拟制度：年假属于带薪假期，正常审批通过后通常不扣工资。";
        }
        return "";
    }

    private void streamFromDify(String question, String user, SseEmitter emitter) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", difyInputs(question, user, Map.of()));
        body.put("query", question);
        body.put("response_mode", "streaming");
        body.put("user", user);

        RequestCallback requestCallback = request -> {
            request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            request.getHeaders().setBearerAuth(difyProperties.getApiKey());
            objectMapper.writeValue(request.getBody(), body);
        };

        ResponseExtractor<Void> responseExtractor = response -> {
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException("DIFY_REQUEST_FAILED", "调用 Dify 流式接口失败：" + response.getStatusCode());
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleDifyStreamLine(line, emitter);
                }
            }
            sendDone(emitter);
            return null;
        };

        try {
            restTemplate.execute(
                    difyProperties.getBaseUrl() + "/chat-messages",
                    HttpMethod.POST,
                    requestCallback,
                    responseExtractor
            );
        } catch (RestClientException exception) {
            throw new BusinessException("DIFY_REQUEST_FAILED", "调用 Dify 流式接口失败：" + exception.getMessage());
        }
    }

    private void handleDifyStreamLine(String line, SseEmitter emitter) throws IOException {
        if (!line.startsWith("data:")) {
            return;
        }
        String payload = line.substring("data:".length()).trim();
        if (!StringUtils.hasText(payload) || "[DONE]".equals(payload)) {
            return;
        }

        Map<String, Object> event = objectMapper.readValue(payload, new TypeReference<>() {
        });
        Object answerValue = event.get("answer");
        if (answerValue != null && StringUtils.hasText(String.valueOf(answerValue))) {
            emitter.send(SseEmitter.event().name("answer").data(String.valueOf(answerValue)));
        }
        Object conversationId = event.get("conversation_id");
        if (conversationId != null) {
            emitter.send(SseEmitter.event().name("meta").data(Map.of(
                    "source", "dify-employee-handbook",
                    "conversationId", String.valueOf(conversationId)
            )));
        }
    }

    private void streamFallback(String question, SseEmitter emitter) throws IOException {
        emitter.send(SseEmitter.event().name("meta").data(Map.of(
                "source", "mock-employee-handbook"
        )));
        String answer = fallback(question).getAnswer();
        for (int index = 0; index < answer.length(); index += 8) {
            int end = Math.min(index + 8, answer.length());
            emitter.send(SseEmitter.event().name("answer").data(answer.substring(index, end)));
        }
        sendDone(emitter);
    }

    private void sendDone(SseEmitter emitter) throws IOException {
        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
        emitter.complete();
    }

    private void sendError(SseEmitter emitter, Exception exception) {
        try {
            emitter.send(SseEmitter.event().name("error").data(exception.getMessage()));
        } catch (IOException ignored) {
            // Connection may already be closed by the browser.
        }
        emitter.completeWithError(exception);
    }
}
