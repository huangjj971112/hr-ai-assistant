package com.example.hrai.service.dify;

import com.example.hrai.config.DifyProperties;
import com.example.hrai.dto.knowledge.KnowledgeAskResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DifyChatClientTest {

    private HttpServer server;
    private final List<String> authorizationHeaders = new ArrayList<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldProvideLocalSickLeavePolicyWhenDifyIsNotConfigured() {
        DifyProperties properties = new DifyProperties();
        properties.setEnabled(false);
        DifyChatClient client = new DifyChatClient(properties, new ObjectMapper());

        KnowledgeAskResponse response = client.ask("我想请病假，会不会扣工资？", "zhangsan",
                Map.of("toolToken", "tool-token"));

        assertThat(response.getSource()).isEqualTo("mock-employee-handbook");
        assertThat(response.getAnswer()).contains("员工手册智能问答尚未连接 Dify");
        assertThat(response.getAnswer()).contains("病假工资", "按比例", "扣款");
    }

    @Test
    void shouldProvideLocalAnnualLeavePolicyWhenDifyIsNotConfigured() {
        DifyProperties properties = new DifyProperties();
        properties.setEnabled(false);
        DifyChatClient client = new DifyChatClient(properties, new ObjectMapper());

        KnowledgeAskResponse response = client.ask("年假会不会影响工资？", "zhangsan");

        assertThat(response.getAnswer()).contains("年假属于带薪假期", "通常不扣工资");
    }

    @Test
    void shouldUseWorkflowOutputsAsPolicyKnowledgeWhenWorkflowKeyIsConfigured() throws IOException {
        startDifyStub();
        DifyProperties properties = new DifyProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setApiKey("workflow-key");
        DifyChatClient client = new DifyChatClient(properties, new ObjectMapper());

        KnowledgeAskResponse response = client.ask("我想请病假，会不会扣工资？", "zhangsan",
                Map.of("toolToken", "tool-token"));

        assertThat(response.getSource()).isEqualTo("员工手册-请假考勤薪酬制度");
        assertThat(response.getAnswer()).isEqualTo("病假可能按病假工资规则影响工资。");
        assertThat(authorizationHeaders).containsExactly("Bearer workflow-key");
    }

    @Test
    void shouldKeepLegacyWorkflowOutputFieldAsFallback() throws IOException {
        startDifyStub();
        DifyProperties properties = new DifyProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setApiKey("legacy-workflow-key");
        DifyChatClient client = new DifyChatClient(properties, new ObjectMapper());

        KnowledgeAskResponse response = client.ask("我想请病假，会不会扣工资？", "zhangsan");

        assertThat(response.getSource()).isEqualTo("dify-workflow-handbook");
        assertThat(response.getAnswer()).isEqualTo("旧版 output 字段仍可作为制度摘要。");
    }

    @Test
    void shouldFallbackToAgentStreamingWhenConfiguredKeyIsNotChatApp() throws IOException {
        startDifyStub();
        DifyProperties properties = new DifyProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setApiKey("chat-key");
        properties.setAgentApiKey("agent-key");
        DifyChatClient client = new DifyChatClient(properties, new ObjectMapper());

        KnowledgeAskResponse response = client.ask("我想请病假，会不会扣工资？", "zhangsan",
                Map.of("toolToken", "tool-token"));

        assertThat(response.getSource()).isEqualTo("dify-agent-handbook");
        assertThat(response.getAnswer()).isEqualTo("病假工资按制度执行，可能按比例发放。");
        assertThat(authorizationHeaders).containsExactly("Bearer chat-key", "Bearer chat-key", "Bearer agent-key");
    }

    private void startDifyStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/workflows/run", this::handleWorkflowRun);
        server.createContext("/chat-messages", this::handleChatMessages);
        server.start();
    }

    private void handleWorkflowRun(HttpExchange exchange) throws IOException {
        authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if ("Bearer legacy-workflow-key".equals(authorization)) {
            sendJson(exchange, 200, """
                    {
                      "workflow_run_id": "workflow-run-legacy",
                      "data": {
                        "outputs": {
                          "output": "旧版 output 字段仍可作为制度摘要。"
                        }
                      }
                    }
                    """);
            return;
        }
        if (!"Bearer workflow-key".equals(authorization)) {
            sendJson(exchange, 400, "{\"code\":\"not_workflow_app\",\"message\":\"wrong route\",\"status\":400}");
            return;
        }
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (!requestBody.contains("\"employeeName\":\"zhangsan\"")
                || !requestBody.contains("\"toolToken\":\"tool-token\"")
                || !requestBody.contains("\"query\":\"我想请病假，会不会扣工资？\"")) {
            sendJson(exchange, 400, "{\"code\":\"invalid_param\",\"message\":\"query is required\",\"status\":400}");
            return;
        }
        sendJson(exchange, 200, """
                {
                  "workflow_run_id": "workflow-run-1",
                  "data": {
                    "outputs": {
                      "answer": "病假可能按病假工资规则影响工资。",
                      "source": "员工手册-请假考勤薪酬制度"
                    }
                  }
                }
                """);
    }

    private void handleChatMessages(HttpExchange exchange) throws IOException {
        authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if ("Bearer chat-key".equals(authorization)) {
            sendJson(exchange, 400, "{\"code\":\"not_chat_app\",\"message\":\"wrong route\",\"status\":400}");
            return;
        }
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (!requestBody.contains("\"employeeName\":\"zhangsan\"")
                || !requestBody.contains("\"toolToken\":\"tool-token\"")
                || !requestBody.contains("\"currentDateTime\"")) {
            sendJson(exchange, 400, "{\"code\":\"invalid_param\",\"message\":\"employeeName is required in input form\",\"status\":400}");
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream;charset=UTF-8");
        byte[] body = """
                data: {"event":"message","answer":"病假工资按制度执行，"}

                data: {"event":"message","answer":"可能按比例发放。","conversation_id":"conversation-1"}

                data: [DONE]

                """.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
