package com.example.hrai.service.ai;

import com.example.hrai.ai.memory.AgentMemoryService;
import com.example.hrai.ai.memory.PendingLeaveApplyDTO;
import com.example.hrai.ai.tool.HrAgentTools;
import com.example.hrai.dto.leave.LeaveBalanceResponse;
import com.example.hrai.entity.LeaveType;
import com.example.hrai.entity.UserRole;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceToolCallingRoutingTest {

    private static final AuthenticatedUser CURRENT_USER =
            new AuthenticatedUser(1L, "zhangsan", "张三", UserRole.EMPLOYEE);

    @Mock
    private RuleBasedIntentRecognizer intentRecognizer;
    @Mock
    private HrToolService hrToolService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AgentMemoryService agentMemoryService;
    @Mock
    private LeaveRequestParser leaveRequestParser;
    @Mock
    private HrAgentTools hrAgentTools;
    @Mock
    private ObjectProvider<SpringAiToolCallingService> springAiToolCallingServiceProvider;
    @Mock
    private SpringAiToolCallingService springAiToolCallingService;

    private AiChatService service;

    @BeforeEach
    void setUp() {
        service = new AiChatService(
                intentRecognizer,
                hrToolService,
                currentUserService,
                agentMemoryService,
                leaveRequestParser,
                hrAgentTools,
                Clock.fixed(Instant.parse("2026-06-14T04:00:00Z"), ZoneId.of("Asia/Shanghai")),
                springAiToolCallingServiceProvider
        );
        when(currentUserService.currentUser()).thenReturn(CURRENT_USER);
    }

    @Test
    void shouldPreferModelToolCallingForQuery() {
        when(springAiToolCallingServiceProvider.getIfAvailable()).thenReturn(springAiToolCallingService);
        when(springAiToolCallingService.chat("查询我的年假余额", CURRENT_USER, "query-session"))
                .thenReturn("模型已通过 queryLeaveBalance 查询：年假余额 5 天。");

        var response = service.chat("查询我的年假余额", "query-session");

        assertThat(response.getIntent()).isEqualTo("SPRING_AI_TOOL_CALLING");
        assertThat(response.getReply()).contains("queryLeaveBalance");
        verify(intentRecognizer, never()).recognize("查询我的年假余额");
        verify(hrToolService, never()).queryLeaveBalance("张三");
    }

    @Test
    void shouldPreferModelToolCallingForAttendanceQuery() {
        when(springAiToolCallingServiceProvider.getIfAvailable()).thenReturn(springAiToolCallingService);
        when(springAiToolCallingService.chat("查询本月考勤", CURRENT_USER, "attendance-session"))
                .thenReturn("模型已通过 queryAttendance 查询本月考勤。");

        var response = service.chat("查询本月考勤", "attendance-session");

        assertThat(response.getIntent()).isEqualTo("SPRING_AI_TOOL_CALLING");
        assertThat(response.getReply()).contains("queryAttendance");
        verify(hrAgentTools, never()).queryAttendance(any());
    }

    @Test
    void shouldPreferModelToolCallingForLeaveAdvice() {
        when(springAiToolCallingServiceProvider.getIfAvailable()).thenReturn(springAiToolCallingService);
        when(springAiToolCallingService.chat("我下周想请三天年假，帮我看看够不够", CURRENT_USER, "advice-session"))
                .thenReturn("模型已组合调用 queryLeaveBalance 和 queryLeavePolicy。");

        var response = service.chat("我下周想请三天年假，帮我看看够不够", "advice-session");

        assertThat(response.getIntent()).isEqualTo("SPRING_AI_TOOL_CALLING");
        assertThat(response.getReply()).contains("queryLeaveBalance", "queryLeavePolicy");
        verify(hrAgentTools, never()).queryLeaveBalance(any());
        verify(hrAgentTools, never()).queryLeavePolicy(any());
    }

    @Test
    void shouldFallBackToKeywordQueryWhenModelFails() {
        when(springAiToolCallingServiceProvider.getIfAvailable()).thenReturn(springAiToolCallingService);
        when(springAiToolCallingService.chat("查询我的年假余额", CURRENT_USER, "fallback-session"))
                .thenThrow(new IllegalStateException("model unavailable"));
        when(intentRecognizer.recognize("查询我的年假余额")).thenReturn(HrIntent.LEAVE_BALANCE);
        when(hrToolService.queryLeaveBalance("张三")).thenReturn(new LeaveBalanceResponse("张三", 5, 10));

        var response = service.chat("查询我的年假余额", "fallback-session");

        assertThat(response.getIntent()).isEqualTo("LEAVE_BALANCE");
        assertThat(response.getData()).isEqualTo(new LeaveBalanceResponse("张三", 5, 10));
    }

    @Test
    void shouldNeverSendLeaveApplicationToModel() {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 15, 14, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 6, 15, 18, 0);
        ParsedLeaveRequest parsed = new ParsedLeaveRequest(
                LeaveType.ANNUAL, startTime, endTime, "个人事务", List.of()
        );
        PendingLeaveApplyDTO pending = new PendingLeaveApplyDTO(
                "pending_leave_apply", "张三", LeaveType.ANNUAL, startTime, endTime,
                "个人事务", false, LocalDateTime.now(), LocalDateTime.now().plusMinutes(20)
        );
        when(leaveRequestParser.parse("帮我请明天下午年假")).thenReturn(parsed);
        when(agentMemoryService.savePendingLeave(eq(1L), eq("apply-session"), any(PendingLeaveApplyDTO.class)))
                .thenReturn(pending);

        var response = service.chat("帮我请明天下午年假", "apply-session");

        assertThat(response.getIntent()).isEqualTo("LEAVE_APPLY_PENDING");
        verify(springAiToolCallingServiceProvider, never()).getIfAvailable();
        verify(springAiToolCallingService, never()).chat("帮我请明天下午年假", CURRENT_USER, "apply-session");
    }
}
