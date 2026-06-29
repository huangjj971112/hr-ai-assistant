package com.example.hrai.ai.multiagent;

import com.example.hrai.entity.UserRole;
import com.example.hrai.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * 基于大模型的 Multi-Agent 调度 Planner。
 *
 * <p>它只负责把用户问题转换为结构化执行计划，不直接调用任何 HR 业务。
 * 如果模型不可用、输出为空、JSON 解析失败或字段校验不通过，会自动回退到
 * 原有 {@link AgentDispatchPlanner}，保证现有规则链路仍然可用。</p>
 */
@Component
public class LlmAgentDispatchPlanner {

    private static final Logger log = LoggerFactory.getLogger(LlmAgentDispatchPlanner.class);

    private static final Set<String> ALLOWED_AGENTS = Set.of(
            "PolicyAgent", "AttendanceAgent", "LeaveAgent", "SalaryAgent"
    );
    private static final Set<String> POLICY_ACTIONS = Set.of("query_leave_policy");
    private static final Set<String> ATTENDANCE_ACTIONS = Set.of("query_attendance");
    private static final Set<String> LEAVE_ACTIONS = Set.of(
            "query_leave_balance", "query_leave_records", "create_leave_pending",
            "confirm_leave_apply", "cancel_pending"
    );
    private static final Set<String> SALARY_ACTIONS = Set.of(
            "query_salary", "query_payroll", "evaluate_salary_impact"
    );

    private static final String SYSTEM_PROMPT = """
            你是 HR Multi-Agent Planner，只负责生成结构化执行计划。
            不要回答用户问题，不要编造业务数据，不要调用工具。
            可用 Agent：
            - PolicyAgent：查询制度、规则、员工手册、考勤制度、年假制度。action 只能是 query_leave_policy。
            - AttendanceAgent：查询考勤、出勤、迟到、早退、缺卡。action 只能是 query_attendance。
            - LeaveAgent：查询假期余额、请假记录、创建请假待确认、确认请假、取消待确认。
              action 可用 query_leave_balance、query_leave_records、create_leave_pending、confirm_leave_apply、cancel_pending。
            - SalaryAgent：查询工资、薪资、奖金、绩效、社保、公积金或判断薪酬影响。
              action 可用 query_salary、query_payroll、evaluate_salary_impact。
            涉及真实提交请假、补卡、销假等写操作时，只允许规划 pending + confirm 流程，不允许直接提交。
            只输出 JSON，不要 Markdown，不要代码块。
            JSON 格式：
            {
              "needPlan": true,
              "steps": [
                {"agent": "AttendanceAgent", "action": "query_attendance", "reason": "用户想查询考勤"}
              ],
              "needConfirm": false,
              "summary": "需要查询考勤"
            }
            """;

    private final AgentDispatchPlanner rulePlanner;
    private final ObjectMapper objectMapper;
    private final Function<String, String> llmInvoker;

    @Autowired
    public LlmAgentDispatchPlanner(
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectMapper objectMapper,
            AgentDispatchPlanner rulePlanner
    ) {
        this(rulePlanner, objectMapper, prompt -> {
            ChatModel chatModel = chatModelProvider.getIfAvailable();
            if (chatModel == null) {
                throw new PlannerFallbackException("LLM_MODEL_UNAVAILABLE");
            }
            return ChatClient.builder(chatModel).build().prompt()
                    .system(SYSTEM_PROMPT)
                    .user(prompt)
                    .call()
                    .content();
        });
    }

    LlmAgentDispatchPlanner(
            AgentDispatchPlanner rulePlanner,
            ObjectMapper objectMapper,
            Function<String, String> llmInvoker
    ) {
        this.rulePlanner = rulePlanner;
        this.objectMapper = objectMapper;
        this.llmInvoker = llmInvoker;
    }

    /**
     * 路由阶段仍使用规则 Planner 判断是否适合 Multi-Agent，避免每次 supports 都调用模型。
     */
    public boolean supports(String message) {
        return rulePlanner.supports(message);
    }

    /**
     * 优先使用 LLM 生成结构化计划；任何异常或非法输出都会自动回退规则 Planner。
     */
    public AgentDispatchPlanningResult plan(String message, AuthenticatedUser user, LocalDateTime now) {
        String traceId = UUID.randomUUID().toString();
        String rawOutput = null;
        try {
            rawOutput = llmInvoker.apply(buildUserPrompt(message, user, now));
            AgentDispatchPlan plan = parseAndValidate(rawOutput);
            log.info("agent dispatch planner traceId={} plannerType={} rawOutput={} finalPlan={} fallbackReason={}",
                    traceId, AgentPlannerType.LLM, rawOutput, plan, null);
            return new AgentDispatchPlanningResult(plan, AgentPlannerType.LLM, traceId, rawOutput, null);
        } catch (RuntimeException exception) {
            String fallbackReason = fallbackReason(exception);
            AgentDispatchPlan fallbackPlan = rulePlanner.plan(message);
            log.info("agent dispatch planner traceId={} plannerType={} rawOutput={} finalPlan={} fallbackReason={}",
                    traceId, AgentPlannerType.RULE, rawOutput, fallbackPlan, fallbackReason);
            return new AgentDispatchPlanningResult(
                    fallbackPlan, AgentPlannerType.RULE, traceId, rawOutput, fallbackReason
            );
        }
    }

    private String buildUserPrompt(String message, AuthenticatedUser user, LocalDateTime now) {
        AuthenticatedUser resolvedUser = user == null
                ? new AuthenticatedUser(-1L, "unknown", "unknown", UserRole.EMPLOYEE)
                : user;
        return """
                用户问题：%s
                当前用户：
                - userId: %s
                - username: %s
                - employeeName: %s
                - role: %s
                当前时间：%s
                可用 Agent：PolicyAgent, AttendanceAgent, LeaveAgent, SalaryAgent
                请根据用户问题输出固定 JSON 调度计划。
                """.formatted(
                message == null ? "" : message,
                resolvedUser.userId(),
                resolvedUser.username(),
                resolvedUser.employeeName(),
                resolvedUser.role(),
                now
        );
    }

    private AgentDispatchPlan parseAndValidate(String rawOutput) {
        if (!StringUtils.hasText(rawOutput)) {
            throw new PlannerFallbackException("LLM_EMPTY_OUTPUT");
        }
        JsonNode root = readJson(stripCodeFence(rawOutput));
        if (!root.path("needPlan").asBoolean(false)) {
            throw new PlannerFallbackException("LLM_PLAN_NOT_NEEDED");
        }
        JsonNode steps = root.path("steps");
        if (!steps.isArray() || steps.isEmpty()) {
            throw new PlannerFallbackException("LLM_EMPTY_STEPS");
        }

        boolean needPolicy = false;
        boolean needAttendance = false;
        boolean needLeave = false;
        boolean needSalary = false;
        boolean allowWriteAction = root.path("needConfirm").asBoolean(false);
        Set<String> reasons = new LinkedHashSet<>();

        for (JsonNode step : steps) {
            String agent = requiredText(step, "agent");
            String action = requiredText(step, "action");
            String reason = optionalText(step, "reason");
            validateStep(agent, action);
            if (StringUtils.hasText(reason)) {
                reasons.add(reason);
            }
            needPolicy = needPolicy || "PolicyAgent".equals(agent);
            needAttendance = needAttendance || "AttendanceAgent".equals(agent);
            needLeave = needLeave || "LeaveAgent".equals(agent);
            needSalary = needSalary || "SalaryAgent".equals(agent);
            allowWriteAction = allowWriteAction || isWriteAction(action);
        }

        String summary = optionalText(root, "summary");
        String planReason = StringUtils.hasText(summary) ? summary : String.join("；", reasons);
        if (!StringUtils.hasText(planReason)) {
            planReason = "LLM Planner 生成执行计划";
        }
        return new AgentDispatchPlan(
                needPolicy, needAttendance, needLeave, needSalary, allowWriteAction, planReason
        );
    }

    private JsonNode readJson(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (Exception exception) {
            throw new PlannerFallbackException("LLM_JSON_PARSE_FAILED");
        }
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = optionalText(node, fieldName);
        if (!StringUtils.hasText(value)) {
            throw new PlannerFallbackException("LLM_MISSING_FIELD_" + fieldName.toUpperCase(Locale.ROOT));
        }
        return value;
    }

    private String optionalText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private void validateStep(String agent, String action) {
        if (!ALLOWED_AGENTS.contains(agent)) {
            throw new PlannerFallbackException("LLM_UNKNOWN_AGENT_" + agent);
        }
        Set<String> allowedActions = switch (agent) {
            case "PolicyAgent" -> POLICY_ACTIONS;
            case "AttendanceAgent" -> ATTENDANCE_ACTIONS;
            case "LeaveAgent" -> LEAVE_ACTIONS;
            case "SalaryAgent" -> SALARY_ACTIONS;
            default -> Set.of();
        };
        if (!allowedActions.contains(action)) {
            throw new PlannerFallbackException("LLM_INVALID_ACTION_" + agent + "_" + action);
        }
    }

    private boolean isWriteAction(String action) {
        return "create_leave_pending".equals(action)
                || "confirm_leave_apply".equals(action)
                || "cancel_pending".equals(action);
    }

    private String stripCodeFence(String rawOutput) {
        String text = rawOutput.trim();
        if (!text.startsWith("```")) {
            return text;
        }
        int firstLineEnd = text.indexOf('\n');
        int lastFence = text.lastIndexOf("```");
        if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
            return text.substring(firstLineEnd + 1, lastFence).trim();
        }
        return text;
    }

    private String fallbackReason(RuntimeException exception) {
        if (exception instanceof PlannerFallbackException) {
            return exception.getMessage();
        }
        return exception.getClass().getSimpleName();
    }

    private static class PlannerFallbackException extends RuntimeException {

        PlannerFallbackException(String message) {
            super(message);
        }
    }
}
