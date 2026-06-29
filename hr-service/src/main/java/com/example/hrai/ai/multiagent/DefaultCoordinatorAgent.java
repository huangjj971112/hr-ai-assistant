package com.example.hrai.ai.multiagent;

import com.example.hrai.ai.observation.AgentDecisionObservation;
import com.example.hrai.ai.observation.AgentObservationBuilder;
import com.example.hrai.ai.observation.AgentObservationSanitizer;
import com.example.hrai.ai.observation.AgentObservationStatus;
import com.example.hrai.ai.observation.AgentPlannerObservation;
import com.example.hrai.ai.observation.AgentReflectionObservation;
import com.example.hrai.ai.observation.AgentToolObservation;
import com.example.hrai.ai.reflection.ReflectionAction;
import com.example.hrai.ai.reflection.ReflectionContext;
import com.example.hrai.ai.reflection.ReflectionResult;
import com.example.hrai.ai.reflection.ReflectionService;
import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.dto.ai.AiChatResponse;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 默认 Multi-Agent 协调器。
 *
 * <p>执行顺序是：Planner 生成调度计划 -> 各子 Agent 返回结构化事实 ->
 * SalaryAgent 判断薪酬影响 -> Coordinator 汇总成最终回复。这里不写具体 HR 业务规则，
 * 也不直接调用确认提交类工具。</p>
 */
@Service
public class DefaultCoordinatorAgent implements CoordinatorAgent {

    private final LlmAgentDispatchPlanner planner;
    private final CurrentUserService currentUserService;
    private final PolicyAgent policyAgent;
    private final AttendanceAgent attendanceAgent;
    private final LeaveAgent leaveAgent;
    private final SalaryAgent salaryAgent;
    private final ReflectionService reflectionService;
    private final Clock clock;
    private final AgentObservationSanitizer observationSanitizer = new AgentObservationSanitizer();

    public DefaultCoordinatorAgent(
            LlmAgentDispatchPlanner planner,
            CurrentUserService currentUserService,
            PolicyAgent policyAgent,
            AttendanceAgent attendanceAgent,
            LeaveAgent leaveAgent,
            SalaryAgent salaryAgent,
            ReflectionService reflectionService,
            Clock clock
    ) {
        this.planner = planner;
        this.currentUserService = currentUserService;
        this.policyAgent = policyAgent;
        this.attendanceAgent = attendanceAgent;
        this.leaveAgent = leaveAgent;
        this.salaryAgent = salaryAgent;
        this.reflectionService = reflectionService;
        this.clock = clock;
    }

    @Override
    public boolean supports(String message) {
        return planner.supports(message);
    }

    @Override
    public AiChatResponse chat(String message, String sessionId) {
        AuthenticatedUser user = currentUserService.currentUser();
        /*
         * Coordinator 优先让 LLM Planner 生成结构化执行计划。
         * LLM 输出不可用或非法时，planner 内部会自动回退到原规则 Planner。
         */
        AgentDispatchPlanningResult planningResult = planner.plan(message, user, LocalDateTime.now(clock));
        AgentDispatchPlan plan = planningResult.plan();
        AgentInvocationContext context = new AgentInvocationContext(message, sessionId);
        AgentObservationBuilder observationBuilder = new AgentObservationBuilder();
        observationBuilder.planner(new AgentPlannerObservation(
                planningResult.plannerType().name(),
                planningResult.traceId(),
                planningResult.fallbackReason(),
                plan.reason()
        ));

        /*
         * 每个子 Agent 只在计划需要时执行，避免不必要的 MCP/模型/业务调用。
         * 子 Agent 返回的是结构化结果，最终自然语言回复在 buildReply 中统一生成。
         */
        PolicyAgentResult policy = plan.needPolicy() ? observePolicy(observationBuilder, context) : null;
        AttendanceAgentResult attendance = plan.needAttendance()
                ? observeAttendance(observationBuilder, context, resolveAttendanceRange(message))
                : null;
        LeaveAgentResult leave = plan.needLeave() ? observeLeave(observationBuilder, context, plan) : null;
        SalaryImpactResult salary = plan.needSalary()
                ? observeSalary(observationBuilder, new SalaryAgentInput(message, policy, attendance, leave))
                : null;
        if (salary != null) {
            observationBuilder.decision(new AgentDecisionObservation(
                    salary.impactLevel().name(),
                    salary.basis(),
                    salary.needsHumanConfirmation()
            ));
        }

        MultiAgentResult result = new MultiAgentResult(plan, policy, attendance, leave, salary);
        String reply = buildReply(result);
        var observation = observationBuilder.build();
        ReflectionResult reflection = reflectionService.reflect(
                new ReflectionContext(message, plan, observation, reply, result)
        );
        observationBuilder.reflection(reflectionObservation(reflection));
        return new AiChatResponse(
                "MULTI_AGENT_RESPONSE", reflectedReply(reply, reflection), result, observationBuilder.build()
        );
    }

    private AgentReflectionObservation reflectionObservation(ReflectionResult reflection) {
        // 将 Reflection 内部动作转成前端可展示的稳定字段，便于定位最终回复是否被反思层改写。
        return new AgentReflectionObservation(
                reflection.traceId(),
                reflection.ruleAction() == null ? "" : reflection.ruleAction().name(),
                reflection.action().name(),
                reflection.reason(),
                reflection.needRetry(),
                reflection.needReplan(),
                reflection.llmRawOutput()
        );
    }

    private String reflectedReply(String reply, ReflectionResult reflection) {
        if ((reflection.action() == ReflectionAction.ASK_USER || reflection.action() == ReflectionAction.FAIL)
                && !reflection.userMessage().isBlank()) {
            return reflection.userMessage();
        }
        return reply;
    }

    private PolicyAgentResult observePolicy(AgentObservationBuilder observationBuilder, AgentInvocationContext context) {
        AgentObservationBuilder.AgentStepHandle handle = observationBuilder.startAgent("PolicyAgent", "查询制度依据");
        long startedAtNanos = System.nanoTime();
        PolicyAgentResult result = policyAgent.query(context);
        long durationMs = elapsedMillis(startedAtNanos);
        ToolResult<Object> toolResult = ToolResult.success(result.traceId(), "ok", Map.of(
                "answer", result.policySummary() == null ? "" : result.policySummary(),
                "source", result.evidence().isEmpty() ? "" : result.evidence().get(0)
        ));
        observationBuilder.addToolCall(handle, new AgentToolObservation(
                "query_leave_policy",
                status(result.success()),
                durationMs,
                result.traceId(),
                observationSanitizer.sanitizeInput("query_leave_policy", Map.of("question", context.message())),
                observationSanitizer.sanitizeResult("query_leave_policy", toolResult),
                observationSanitizer.evidenceSource("query_leave_policy", toolResult),
                result.success() ? null : "MCP_TOOL_CALL_FAILED"
        ));
        observationBuilder.finishAgent(handle, status(result.success()),
                result.success() ? "PolicyAgent 已返回制度依据" : "PolicyAgent 制度查询失败");
        return result;
    }

    private AttendanceAgentResult observeAttendance(
            AgentObservationBuilder observationBuilder,
            AgentInvocationContext context,
            AttendanceAgent.DateRange dateRange
    ) {
        AgentObservationBuilder.AgentStepHandle handle = observationBuilder.startAgent("AttendanceAgent", "查询考勤事实");
        long startedAtNanos = System.nanoTime();
        AttendanceAgentResult result = attendanceAgent.query(context, dateRange);
        long durationMs = elapsedMillis(startedAtNanos);
        observationBuilder.addToolCall(handle, new AgentToolObservation(
                "query_attendance",
                status(result.success()),
                durationMs,
                result.traceId(),
                observationSanitizer.sanitizeInput("query_attendance", Map.of(
                        "startDate", dateRange.startDate(),
                        "endDate", dateRange.endDate()
                )),
                Map.of("recordCount", result.records().size(), "lateCount", result.lateCount()),
                null,
                result.success() ? null : "MCP_TOOL_CALL_FAILED"
        ));
        observationBuilder.finishAgent(handle, status(result.success()),
                result.success() ? "AttendanceAgent 已返回考勤事实" : "AttendanceAgent 考勤查询失败");
        return result;
    }

    private LeaveAgentResult observeLeave(
            AgentObservationBuilder observationBuilder,
            AgentInvocationContext context,
            AgentDispatchPlan plan
    ) {
        AgentObservationBuilder.AgentStepHandle handle = observationBuilder.startAgent("LeaveAgent", "查询假期或创建待确认申请");
        long startedAtNanos = System.nanoTime();
        LeaveAgentResult result = leaveAgent.evaluate(context, plan);
        long durationMs = elapsedMillis(startedAtNanos);
        String toolName = result.pending() == null ? "query_leave_balance" : "create_leave_pending";
        Object toolData = result.pending() == null ? result.leaveBalance() : result.pending();
        observationBuilder.addToolCall(handle, new AgentToolObservation(
                toolName,
                status(result.success()),
                durationMs,
                result.traceId(),
                observationSanitizer.sanitizeInput(toolName, Map.of("message", context.message())),
                observationSanitizer.sanitizeResult(toolName, ToolResult.success(result.traceId(), "ok", toolData)),
                null,
                result.success() ? null : "MCP_TOOL_CALL_FAILED"
        ));
        observationBuilder.finishAgent(handle, status(result.success()),
                result.success() ? "LeaveAgent 已返回假期信息" : "LeaveAgent 假期信息查询失败");
        return result;
    }

    private SalaryImpactResult observeSalary(
            AgentObservationBuilder observationBuilder,
            SalaryAgentInput input
    ) {
        AgentObservationBuilder.AgentStepHandle handle = observationBuilder.startAgent("SalaryAgent", "判断薪酬影响");
        SalaryImpactResult result = salaryAgent.evaluate(input);
        observationBuilder.finishAgent(handle, AgentObservationStatus.SUCCESS, "SalaryAgent 已完成薪酬影响判断");
        return result;
    }

    private AgentObservationStatus status(boolean success) {
        return success ? AgentObservationStatus.SUCCESS : AgentObservationStatus.FAILED;
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }

    private AttendanceAgent.DateRange resolveAttendanceRange(String message) {
        LocalDate today = LocalDate.now(clock);
        if (message != null && (message.contains("这周") || message.contains("本周"))) {
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return new AttendanceAgent.DateRange(monday, today);
        }
        // 没有明确时间范围时，先用最近 7 天作为保守默认值。
        return new AttendanceAgent.DateRange(today.minusDays(6), today);
    }

    private String buildReply(MultiAgentResult result) {
        List<String> agents = new ArrayList<>();
        if (result.policy() != null) {
            agents.add("PolicyAgent");
        }
        if (result.attendance() != null) {
            agents.add("AttendanceAgent");
        }
        if (result.leave() != null) {
            agents.add("LeaveAgent");
        }
        if (result.salary() != null) {
            agents.add("SalaryAgent");
        }

        String salarySummary = result.salary() == null ? "暂未进行薪酬影响判断。" : result.salary().summary();
        String basis = result.salary() == null ? "" : "\n判断依据：" + result.salary().basis();
        return "已协调 " + String.join("、", agents) + " 进行分析。\n薪酬影响：" + salarySummary + basis;
    }
}
