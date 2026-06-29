package com.example.hrai.ai.controller;

import com.example.hrai.ai.observation.AgentDecisionObservation;
import com.example.hrai.ai.observation.AgentObservationSnapshot;
import com.example.hrai.ai.observation.AgentObservationStatus;
import com.example.hrai.ai.observation.AgentObservationStep;
import com.example.hrai.ai.observation.AgentToolObservation;
import com.example.hrai.ai.service.HrMcpChatService;
import com.example.hrai.dto.ai.AiChatRequest;
import com.example.hrai.dto.ai.AiChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HrMcpChatControllerTest {

    @Test
    void shouldDelegateAuthenticatedChatRequestToMcpChatService() {
        HrMcpChatService service = mock(HrMcpChatService.class);
        HrMcpChatController controller = new HrMcpChatController(service);
        AiChatRequest request = new AiChatRequest();
        request.setMessage("查询我的年假余额");
        request.setSessionId("frontend-session");
        when(service.chat(request.getMessage(), request.getSessionId()))
                .thenReturn(new AiChatResponse("LEAVE_BALANCE", "假期余额查询成功", null));

        AiChatResponse response = controller.chat(request);

        assertThat(response.getIntent()).isEqualTo("LEAVE_BALANCE");
        verify(service).chat("查询我的年假余额", "frontend-session");
    }

    @Test
    void shouldSerializeObservationWithRequestIdAndTotalDuration() throws Exception {
        HrMcpChatService service = mock(HrMcpChatService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HrMcpChatController(service)).build();
        AgentToolObservation tool = new AgentToolObservation(
                "query_leave_balance",
                AgentObservationStatus.SUCCESS,
                35L,
                "trace-001",
                Map.of("employeeScope", "SELF"),
                Map.of("remainingDays", 5),
                "leave_balance",
                null
        );
        AgentObservationStep step = new AgentObservationStep(
                "leave-agent",
                AgentObservationStatus.SUCCESS,
                40L,
                "完成假期余额查询",
                List.of(tool)
        );
        AgentObservationSnapshot observation = new AgentObservationSnapshot(
                "request-001",
                AgentObservationStatus.SUCCESS,
                45L,
                List.of("识别查询意图", "调用假期余额工具"),
                List.of(step),
                null,
                null,
                new AgentDecisionObservation("RETURN_RESULT", "工具返回有效余额", false)
        );
        AiChatResponse response = new AiChatResponse(
                "LEAVE_BALANCE",
                "假期余额查询成功",
                Map.of("remainingDays", 5),
                observation
        );
        when(service.chat("查询我的年假余额", "frontend-session")).thenReturn(response);

        mockMvc.perform(post("/api/ai/mcp/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "查询我的年假余额",
                                  "sessionId": "frontend-session"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("LEAVE_BALANCE"))
                .andExpect(jsonPath("$.reply").value("假期余额查询成功"))
                .andExpect(jsonPath("$.data.remainingDays").value(5))
                .andExpect(jsonPath("$.observation.requestId").value("request-001"))
                .andExpect(jsonPath("$.observation.totalDurationMs").value(45));
    }

    @Test
    void shouldKeepObservationNullForThreeArgumentConstructor() {
        Map<String, Object> data = Map.of("remainingDays", 5);
        AiChatResponse response = new AiChatResponse("LEAVE_BALANCE", "假期余额查询成功", data);

        assertThat(response.getIntent()).isEqualTo("LEAVE_BALANCE");
        assertThat(response.getReply()).isEqualTo("假期余额查询成功");
        assertThat(response.getData()).isEqualTo(data);
        assertThat(response.getObservation()).isNull();
    }

    @Test
    void shouldDefensivelyCopyObservationCollections() {
        Map<String, Object> inputSummary = new HashMap<>(Map.of("employeeScope", "SELF"));
        Map<String, Object> resultSummary = new HashMap<>(Map.of("remainingDays", 5));
        AgentToolObservation tool = new AgentToolObservation(
                "query_leave_balance",
                AgentObservationStatus.SUCCESS,
                35L,
                null,
                inputSummary,
                resultSummary,
                null,
                null
        );
        List<AgentToolObservation> toolCalls = new ArrayList<>(List.of(tool));
        AgentObservationStep step = new AgentObservationStep(
                "leave-agent",
                AgentObservationStatus.SUCCESS,
                40L,
                "完成假期余额查询",
                toolCalls
        );
        List<String> summarySteps = new ArrayList<>(List.of("识别查询意图"));
        List<AgentObservationStep> steps = new ArrayList<>(List.of(step));
        AgentObservationSnapshot snapshot = new AgentObservationSnapshot(
                "request-001",
                AgentObservationStatus.SUCCESS,
                45L,
                summarySteps,
                steps,
                null,
                null,
                null
        );

        inputSummary.put("employeeScope", "OTHER");
        resultSummary.put("remainingDays", 0);
        toolCalls.clear();
        summarySteps.clear();
        steps.clear();

        assertThat(tool.inputSummary()).containsExactlyEntriesOf(Map.of("employeeScope", "SELF"));
        assertThat(tool.resultSummary()).containsExactlyEntriesOf(Map.of("remainingDays", 5));
        assertThat(step.toolCalls()).containsExactly(tool);
        assertThat(snapshot.summarySteps()).containsExactly("识别查询意图");
        assertThat(snapshot.steps()).containsExactly(step);
    }

    @Test
    void shouldNormalizeNullObservationCollectionsToEmptyCollections() {
        AgentToolObservation tool = new AgentToolObservation(
                "query_leave_balance",
                AgentObservationStatus.SUCCESS,
                35L,
                null,
                null,
                null,
                null,
                null
        );
        AgentObservationStep step = new AgentObservationStep(
                "leave-agent",
                AgentObservationStatus.SUCCESS,
                40L,
                null,
                null
        );
        AgentObservationSnapshot snapshot = new AgentObservationSnapshot(
                "request-001",
                AgentObservationStatus.SUCCESS,
                45L,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(tool.inputSummary()).isEmpty();
        assertThat(tool.resultSummary()).isEmpty();
        assertThat(step.toolCalls()).isEmpty();
        assertThat(snapshot.summarySteps()).isEmpty();
        assertThat(snapshot.steps()).isEmpty();
    }
}
