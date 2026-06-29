package com.example.hrai.ai.service;

import com.example.hrai.dto.ai.AiChatResponse;
import com.example.hrai.entity.UserRole;
import com.example.hrai.exception.BusinessException;
import com.example.hrai.ai.multiagent.CoordinatorAgent;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrMcpChatServiceTest {

    private static final String SESSION_ID = "frontend-session";

    @Mock
    private McpModelAgent modelAgent;

    @Mock
    private CoordinatorAgent coordinatorAgent;

    @Mock
    private CurrentUserService currentUserService;

    private HrMcpChatService service;

    @BeforeEach
    void setUp() {
        PendingLeaveClarificationMemory clarificationMemory = new PendingLeaveClarificationMemory(
                Clock.fixed(Instant.parse("2026-06-28T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );
        service = new HrMcpChatService(modelAgent, coordinatorAgent, currentUserService, clarificationMemory);
        when(currentUserService.currentUser()).thenReturn(new AuthenticatedUser(
                1L, "zhangsan", "张三", UserRole.EMPLOYEE
        ));
    }

    @Test
    void shouldDelegateAllRoutingToModelAgent() {
        AiChatResponse expected = new AiChatResponse("MCP_MODEL_RESPONSE", "余额还有 5 天", null);
        when(modelAgent.chat("查询我的年假余额", SESSION_ID)).thenReturn(expected);

        AiChatResponse response = service.chat("查询我的年假余额", SESSION_ID);

        assertThat(response).isSameAs(expected);
        verify(coordinatorAgent, never()).chat("查询我的年假余额", SESSION_ID);
    }

    @Test
    void shouldRouteSalaryImpactQuestionsToCoordinatorAgent() {
        AiChatResponse expected = new AiChatResponse("MULTI_AGENT_RESPONSE", "会综合制度和假期信息判断。", null);
        when(coordinatorAgent.supports("我下周想请三天年假，会不会影响工资？")).thenReturn(true);
        when(coordinatorAgent.chat("我下周想请三天年假，会不会影响工资？", SESSION_ID)).thenReturn(expected);

        AiChatResponse response = service.chat("我下周想请三天年假，会不会影响工资？", SESSION_ID);

        assertThat(response).isSameAs(expected);
        verify(modelAgent, never()).chat("我下周想请三天年假，会不会影响工资？", SESSION_ID);
    }

    @Test
    void shouldExposeStrictModelFailureWithoutKeywordFallback() {
        when(modelAgent.chat("确认", SESSION_ID))
                .thenThrow(new BusinessException("MCP_MODEL_UNAVAILABLE", "MCP 大模型未配置"));

        assertThatThrownBy(() -> service.chat("确认", SESSION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("MCP_MODEL_UNAVAILABLE");
    }

    @Test
    void shouldMergeLeaveTypeAnswerWithPreviousIncompleteLeaveRequest() {
        when(modelAgent.chat("帮我请明天的假", SESSION_ID))
                .thenReturn(new AiChatResponse("MCP_MODEL_RESPONSE", "请补充请假类型", null));
        when(modelAgent.chat("帮我请明天的假，请假类型是年假", SESSION_ID))
                .thenReturn(new AiChatResponse("MCP_MODEL_RESPONSE", "已创建待确认年假申请", null));

        service.chat("帮我请明天的假", SESSION_ID);
        AiChatResponse response = service.chat("年假", SESSION_ID);

        assertThat(response.getReply()).isEqualTo("已创建待确认年假申请");
        verify(modelAgent).chat("帮我请明天的假，请假类型是年假", SESSION_ID);
    }

    @Test
    void shouldMergeLeaveTypeAndDurationAnswerWithPreviousIncompleteLeaveRequest() {
        when(modelAgent.chat("帮我请明天的假", SESSION_ID))
                .thenReturn(new AiChatResponse("MCP_MODEL_RESPONSE", "请补充请假类型和时长", null));
        when(modelAgent.chat("帮我请明天的假，请假类型是年假，一天", SESSION_ID))
                .thenReturn(new AiChatResponse("MCP_MODEL_RESPONSE", "已创建待确认年假申请", null));

        service.chat("帮我请明天的假", SESSION_ID);
        AiChatResponse response = service.chat("年假，一天", SESSION_ID);

        assertThat(response.getReply()).isEqualTo("已创建待确认年假申请");
        verify(modelAgent).chat("帮我请明天的假，请假类型是年假，一天", SESSION_ID);
    }
}
