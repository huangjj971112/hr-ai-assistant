package com.example.hrai.service.dify;

import com.example.hrai.config.DifyProperties;
import com.example.hrai.dto.knowledge.KnowledgeAskResponse;
import com.example.hrai.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class DifyChatClient {

    private final DifyProperties difyProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isReady() {
        return difyProperties.isEnabled() && StringUtils.hasText(difyProperties.getApiKey());
    }

    public KnowledgeAskResponse ask(String question, String user) {
        if (!isReady()) {
            return fallback(question);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(difyProperties.getApiKey());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", Map.of());
        body.put("query", question);
        body.put("response_mode", "blocking");
        body.put("user", user);

        try {
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
        } catch (RestClientException exception) {
            throw new BusinessException("DIFY_REQUEST_FAILED", "调用 Dify 失败：" + exception.getMessage());
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
        String answer = "员工手册智能问答尚未连接 Dify。请配置 DIFY_API_KEY，并在 Dify 应用知识库中上传员工手册后重试。当前问题：" + question;
        return new KnowledgeAskResponse(answer, "mock-employee-handbook", null);
    }

    private void streamFromDify(String question, String user, SseEmitter emitter) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", Map.of());
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
