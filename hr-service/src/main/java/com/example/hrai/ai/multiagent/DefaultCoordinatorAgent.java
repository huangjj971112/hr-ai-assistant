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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        /*
         * chat 是 Multi-Agent 对话的总入口，负责把一次用户问题串成完整链路：
         *
         * 用户问题
         * -> Planner 判断需要哪些子 Agent
         * -> Coordinator 按计划调用子 Agent
         * -> 子 Agent 返回结构化事实
         * -> Coordinator 汇总最终回复
         * -> Reflection 检查最终回复是否可靠
         * -> 返回给前端
         *
         * 注意：Coordinator 只做“调度”和“汇总”，不直接写请假、考勤、薪酬等业务规则。
         */
        AuthenticatedUser user = currentUserService.currentUser();
        /*
         * Coordinator 优先让 LLM Planner 生成结构化执行计划。
         * LLM 输出不可用或非法时，planner 内部会自动回退到原规则 Planner。
         *
         * planningResult 里不仅有最终 plan，还包含 plannerType、traceId、rawOutput、
         * fallbackReason 等观测字段，方便前端展示“本次为什么这样调度”。
         */
        AgentDispatchPlanningResult planningResult = planner.plan(message, user, LocalDateTime.now(clock));
        AgentDispatchPlan plan = planningResult.plan();
        /*
         * AgentInvocationContext 是传给子 Agent 的轻量上下文。
         * 当前主要包含用户原始问题和 sessionId；sessionId 用于 pending 请假等多轮确认场景。
         */
        AgentInvocationContext context = new AgentInvocationContext(message, sessionId);
        /*
         * observationBuilder 用来收集本次 Multi-Agent 的调用链：
         * Planner、每个子 Agent、每次 Tool 调用、最终决策和 Reflection 结果。
         * 前端“点击查看本次 Agent 调用链”展示的内容就来自这里。
         */
        AgentObservationBuilder observationBuilder = new AgentObservationBuilder();
        observationBuilder.planner(new AgentPlannerObservation(
                planningResult.plannerType().name(),
                planningResult.traceId(),
                planningResult.fallbackReason(),
                plan.reason()
        ));

        /*
         * 每个子 Agent 只在 plan.steps 中出现时执行，避免不必要的 MCP/模型/业务调用。
         * 子 Agent 返回的是结构化结果，最终自然语言回复在 buildReply 中统一生成。
         *
         * 例如：
         * - 问“年假制度”通常只需要 PolicyAgent；
         * - 问“这周迟到两次会不会影响工资”通常需要 AttendanceAgent + PolicyAgent + SalaryAgent；
         * - 问“我下周想请三天年假会不会影响工资”通常需要 PolicyAgent + LeaveAgent + SalaryAgent；
         * - 问“本月实发 3000，应发 4000”可以由 LLM Planner 安排 SalaryAgent 先执行。
         */
        PolicyAgentResult policy = null;
        AttendanceAgentResult attendance = null;
        LeaveAgentResult leave = null;
        SalaryImpactResult salary = null;
        Set<String> executedAgents = new LinkedHashSet<>();
        for (AgentDispatchStep step : plan.steps()) {
            // 当前每个子 Agent 在一次请求里最多执行一次；如果 LLM 重复规划同一 Agent，保留第一次执行结果。
            if (!executedAgents.add(step.agent())) {
                continue;
            }
            switch (step.agent()) {
                case "PolicyAgent" -> policy = observePolicy(observationBuilder, context);
                case "AttendanceAgent" -> attendance = observeAttendance(
                        observationBuilder, context, resolveAttendanceRange(message));
                case "LeaveAgent" -> leave = observeLeave(observationBuilder, context, plan);
                case "SalaryAgent" -> salary = observeSalary(
                        observationBuilder, new SalaryAgentInput(message, policy, attendance, leave));
                default -> {
                    // Planner 已在上游做白名单校验；这里保留 default，防止未来手写 Plan 时出现未知 Agent。
                }
            }
        }
        if (salary != null) {
            // SalaryAgent 的结论会作为最终决策写入观测面板，例如 POSSIBLE_IMPACT / NO_IMPACT / UNKNOWN。
            observationBuilder.decision(new AgentDecisionObservation(
                    salary.impactLevel().name(),
                    salary.basis(),
                    salary.needsHumanConfirmation()
            ));
        }

        /*
         * MultiAgentResult 是本次协调结果的结构化载体。
         * 它会同时用于：
         * 1. buildReply 生成用户可读的自然语言回复；
         * 2. ReflectionService 判断计划是否完成、Tool 结果是否足够、回答是否可靠；
         * 3. AiChatResponse.data 返回给前端，便于调试或扩展页面展示。
         */
        MultiAgentResult result = new MultiAgentResult(plan, policy, attendance, leave, salary);
        String reply = buildReply(result);
        /*
         * 这里先 build 一次 observation，作为 Reflection 的输入。
         * Reflection 需要看到 Planner、Agent、Tool 的执行情况，才能判断最终回答是否需要追问或拦截。
         */
        var observation = observationBuilder.build();
        ReflectionResult reflection = reflectionService.reflect(
                new ReflectionContext(message, plan, observation, reply, result)
        );
        /*
         * Reflection 执行完成后，再把反思结果补回 observationBuilder。
         * 这样前端最终看到的调用链会同时包含“执行过程”和“返回前检查结果”。
         */
        observationBuilder.reflection(reflectionObservation(reflection));
        /*
         * reflectedReply 会在 Reflection 要求 ASK_USER 或 FAIL 时，用 userMessage 覆盖原回复；
         * 其他 PASS / REPLAN 记录型场景则保留原始 reply。
         */
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
        long startedAtNanos = System.nanoTime();
        SalaryImpactResult result = salaryAgent.evaluate(input);
        long durationMs = elapsedMillis(startedAtNanos);
        if (result.traceId() != null && !result.traceId().isBlank()) {
            // 工资明细类问题会真正调用 query_salary；把 Tool 结果挂到观测链，方便前端确认走的是工资接口。
            observationBuilder.addToolCall(handle, new AgentToolObservation(
                    "query_salary",
                    AgentObservationStatus.SUCCESS,
                    durationMs,
                    result.traceId(),
                    observationSanitizer.sanitizeInput("query_salary", Map.of("salaryMonth", result.salaryMonth())),
                    observationSanitizer.sanitizeResult(
                            "query_salary",
                            ToolResult.success(result.traceId(), "ok", result.salaryDetail())
                    ),
                    null,
                    null
            ));
        }
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
        Set<String> added = new LinkedHashSet<>();
        for (AgentDispatchStep step : result.dispatchPlan().steps()) {
            if (agentExecuted(result, step.agent()) && added.add(step.agent())) {
                agents.add(step.agent());
            }
        }

        String salarySummary = result.salary() == null ? "暂未进行薪酬影响判断。" : result.salary().summary();
        String basis = result.salary() == null ? "" : "\n判断依据：" + result.salary().basis();
        return "已协调 " + String.join("、", agents) + " 进行分析。\n薪酬影响：" + salarySummary + basis;
    }

    private boolean agentExecuted(MultiAgentResult result, String agentName) {
        return switch (agentName) {
            case "PolicyAgent" -> result.policy() != null;
            case "AttendanceAgent" -> result.attendance() != null;
            case "LeaveAgent" -> result.leave() != null;
            case "SalaryAgent" -> result.salary() != null;
            default -> false;
        };
    }
}
