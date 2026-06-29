package com.example.hrai.ai.service;

import com.example.hrai.ai.mcp.client.HrMcpCaller;
import com.example.hrai.ai.mcp.dto.PendingLeaveMcpResult;
import com.example.hrai.ai.memory.AgentMemoryService;
import com.example.hrai.ai.memory.PendingLeaveApplyDTO;
import com.example.hrai.ai.observation.AgentObservationBuilder;
import com.example.hrai.ai.observation.AgentObservationSanitizer;
import com.example.hrai.ai.observation.AgentObservationStatus;
import com.example.hrai.ai.observation.AgentToolObservation;
import com.example.hrai.ai.reflection.ReflectionAction;
import com.example.hrai.ai.reflection.ReflectionContext;
import com.example.hrai.ai.reflection.ReflectionResult;
import com.example.hrai.ai.reflection.ReflectionService;
import com.example.hrai.ai.security.ToolTokenService;
import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.dto.ai.AiChatResponse;
import com.example.hrai.dto.leave.LeaveApplyResponse;
import com.example.hrai.exception.BusinessException;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 使用 Spring AI ChatClient 实现的 MCP 模型 Agent。
 *
 * <p>大模型负责理解用户意图并自主选择 Tool；内部的 {@link ModelFacingMcpTools}
 * 负责把模型调用安全地转发到真实 MCP Server。模型不会接触 toolToken、
 * sessionId、pendingId 或 idempotencyKey 等受保护字段。</p>
 *
 * <p>/api/ai/mcp/chat 走到这里后，完整链路是：
 * ChatClient -> 大模型选择 Tool -> ModelFacingMcpTools -> HrMcpCaller ->
 * MCP /mcp -> HrMcpTools -> HrMcpToolService -> 现有 HR Service。</p>
 */
@Service
public class SpringAiMcpModelAgent implements McpModelAgent {

    private static final String TENANT_ID = "demo-tenant";
    private static final int MAX_TOOL_CALLS_PER_REQUEST = 4;
    private static final Set<String> SCOPES = Set.of(
            "leave:balance:read", "attendance:records:read", "leave:policy:read", "leave:apply"
    );
    private static final Set<String> CONFIRM_WORDS = Set.of("确认", "确定", "提交");
    private static final Set<String> CANCEL_WORDS = Set.of("取消", "算了", "不提交");
    private static final String SYSTEM_PROMPT = """
            你是企业员工 HR 助手。根据用户意图自主选择提供的 MCP 工具。
            所有业务数据必须通过工具获取，不得编造。
            请假必须先调用 create_leave_pending，向用户展示待确认内容；只有用户明确确认后才能调用 confirm_leave_apply。
            用户取消时调用 cancel_pending。工具失败时清晰说明错误，不得绕过工具或改用关键词规则。
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final CurrentUserService currentUserService;
    private final ToolTokenService toolTokenService;
    private final HrMcpCaller hrMcpCaller;
    private final AgentMemoryService memoryService;
    private final ReflectionService reflectionService;
    private final Clock clock;
    private final AgentToolCallLogger toolCallLogger = new AgentToolCallLogger();
    private final LeaveBalanceReplyPostProcessor leaveBalanceReplyPostProcessor = new LeaveBalanceReplyPostProcessor();
    private final PendingLeaveReplyPostProcessor pendingLeaveReplyPostProcessor = new PendingLeaveReplyPostProcessor();
    private final AgentObservationSanitizer observationSanitizer = new AgentObservationSanitizer();

    public SpringAiMcpModelAgent(
            ObjectProvider<ChatModel> chatModelProvider,
            CurrentUserService currentUserService,
            ToolTokenService toolTokenService,
            HrMcpCaller hrMcpCaller,
            AgentMemoryService memoryService,
            ReflectionService reflectionService,
            Clock agentClock
    ) {
        this.chatModelProvider = chatModelProvider;
        this.currentUserService = currentUserService;
        this.toolTokenService = toolTokenService;
        this.hrMcpCaller = hrMcpCaller;
        this.memoryService = memoryService;
        this.reflectionService = reflectionService;
        this.clock = agentClock;
    }

    @Override
    public AiChatResponse chat(String message, String sessionId) {
        AuthenticatedUser user = currentUserService.currentUser();
        /*
         * Tool Token 是后端给 MCP Tool 的短期授权凭证。
         * 浏览器和模型都不能传入这个 token，防止用户绕过权限直接调用内部 Tool。
         */
        String token = toolTokenService.createToken(user, TENANT_ID, SCOPES);
        AgentObservationBuilder observationBuilder = new AgentObservationBuilder();
        AgentObservationBuilder.AgentStepHandle modelStep = observationBuilder.startAgent(
                "SpringAiMcpModelAgent", "模型理解意图并选择 MCP Tool"
        );
        ModelFacingMcpTools tools = new ModelFacingMcpTools(
                user, token, sessionId, message, observationBuilder, modelStep
        );
        if (isConfirmWord(message) || isCancelWord(message)) {
            return handlePendingDecision(message, user, sessionId, observationBuilder, modelStep, tools);
        }
        // 运行时获取模型，避免 ChatModel 自动配置顺序影响 Agent Bean 的创建。
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new BusinessException("MCP_MODEL_UNAVAILABLE", "MCP 大模型未配置，无法自主选择工具");
        }
        try {
            /*
             * 这是模型自主决策与 Tool Calling 的执行入口：
             * 1. system/user 向模型提供规则、上下文和用户消息；
             * 2. tools 向模型提供允许调用的工具定义；
             * 3. call 自动完成“模型选择工具 -> 执行工具 -> 把结果交回模型”的循环；
             * 4. content 返回模型基于工具结果生成的最终自然语言回复。
             */
            String reply = ChatClient.builder(chatModel).build().prompt()
                    .system(SYSTEM_PROMPT + "\n当前员工：" + user.employeeName()
                            + "\n当前时间：" + LocalDateTime.now(clock)
                            + "\n当前会话：" + sessionId)
                    .user(message)
                    .tools(tools)
                    .call()
                    .content();
            /*
             * 大模型的自然语言回复可能不够稳定，所以这里做两层后处理：
             * 1. 只问年假时，避免把病假余额也答出来；
             * 2. 创建 pending 成功时，强制展示待确认申请和“确认/取消”入口。
             */
            reply = leaveBalanceReplyPostProcessor.refine(message, reply, tools.toolResults);
            // 创建 pending 属于写操作前置步骤，最终答复必须明确展示待确认内容和确认/取消入口。
            reply = pendingLeaveReplyPostProcessor.refine(reply, tools.toolResults);
            // data 里带回当前会话的 pending，前端可用它展示确认卡片或调试信息。
            Object data = memoryService.getPendingLeave(user.userId(), sessionId)
                    .map(PendingLeaveMcpResult::from)
                    .orElse(null);
            observationBuilder.finishAgent(modelStep, AgentObservationStatus.SUCCESS, "模型已生成最终回复");
            return responseWithReflection(message, reply, data, observationBuilder);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException("MCP_MODEL_CALL_FAILED", "MCP 大模型调用失败，请稍后重试");
        }
    }

    private AiChatResponse handlePendingDecision(
            String message,
            AuthenticatedUser user,
            String sessionId,
            AgentObservationBuilder observationBuilder,
            AgentObservationBuilder.AgentStepHandle modelStep,
            ModelFacingMcpTools tools
    ) {
        /*
         * “确认/取消”是强状态操作，不再交给模型猜测。只要当前会话存在 pending，
         * 就直接走对应 MCP Tool；如果没有 pending，返回明确提示，避免模型反问。
         */
        if (memoryService.getPendingLeave(user.userId(), sessionId).isEmpty()) {
            observationBuilder.finishAgent(modelStep, AgentObservationStatus.SUCCESS, "当前没有待确认申请");
            return responseWithReflection(message, "当前没有待确认申请。", null, observationBuilder);
        }
        ToolResult<Object> result = isConfirmWord(message) ? tools.confirmLeaveApply() : tools.cancelPending();
        observationBuilder.finishAgent(modelStep, status(result.success()), "已处理待确认申请");
        Object data = memoryService.getPendingLeave(user.userId(), sessionId)
                .map(PendingLeaveMcpResult::from)
                .orElse(null);
        if (result.success() && isConfirmWord(message)) {
            return responseWithReflection(message, confirmReply(result), data, observationBuilder);
        }
        if (result.success()) {
            return responseWithReflection(message, "已取消本次待确认的请假申请。", data, observationBuilder);
        }
        return responseWithReflection(message, result.message(), data, observationBuilder);
    }

    private AiChatResponse responseWithReflection(
            String originalMessage,
            String reply,
            Object data,
            AgentObservationBuilder observationBuilder
    ) {
        var observation = observationBuilder.build();
        ReflectionResult reflection = reflectionService.reflect(
                new ReflectionContext(originalMessage, null, observation, reply, data)
        );
        String finalReply = reflectedReply(reply, reflection);
        return new AiChatResponse("MCP_MODEL_RESPONSE", finalReply, data, observation);
    }

    private String reflectedReply(String reply, ReflectionResult reflection) {
        if ((reflection.action() == ReflectionAction.ASK_USER || reflection.action() == ReflectionAction.FAIL)
                && !reflection.userMessage().isBlank()) {
            return reflection.userMessage();
        }
        return reply;
    }

    private String confirmReply(ToolResult<Object> result) {
        Object data = result.data();
        if (data instanceof LeaveApplyResponse response) {
            return response.getMessage() + "，申请编号：" + response.getApplyNo();
        }
        if (data instanceof Map<?, ?> map && map.get("applyNo") != null) {
            return result.message() + "，申请编号：" + map.get("applyNo");
        }
        return result.message();
    }

    private boolean isConfirmWord(String message) {
        return CONFIRM_WORDS.contains(message == null ? "" : message.trim());
    }

    private boolean isCancelWord(String message) {
        return CANCEL_WORDS.contains(message == null ? "" : message.trim());
    }

    private AgentObservationStatus status(boolean success) {
        return success ? AgentObservationStatus.SUCCESS : AgentObservationStatus.FAILED;
    }

    private class ModelFacingMcpTools {

        private final AuthenticatedUser user;
        private final String token;
        private final String sessionId;
        private final String originalUserMessage;
        private final AgentObservationBuilder observationBuilder;
        private final AgentObservationBuilder.AgentStepHandle modelStep;
        private final AgentToolCallGuard callGuard = new AgentToolCallGuard(MAX_TOOL_CALLS_PER_REQUEST);
        private final Map<String, ToolResult<Object>> toolResults = new LinkedHashMap<>();

        private ModelFacingMcpTools(
                AuthenticatedUser user,
                String token,
                String sessionId,
                String originalUserMessage,
                AgentObservationBuilder observationBuilder,
                AgentObservationBuilder.AgentStepHandle modelStep
        ) {
            this.user = user;
            this.token = token;
            this.sessionId = sessionId;
            this.originalUserMessage = originalUserMessage;
            this.observationBuilder = observationBuilder;
            this.modelStep = modelStep;
        }

        @Tool(name = "query_leave_balance", description = "查询当前员工的假期余额")
        public ToolResult<Object> queryLeaveBalance() {
            // 查询类 Tool 不接收 employeeName，后端会从 toolToken 解析当前员工，避免越权查询。
            return call("query_leave_balance", Map.of());
        }

        @Tool(name = "query_attendance", description = "查询当前员工指定日期范围的考勤，日期格式 yyyy-MM-dd")
        public ToolResult<Object> queryAttendance(String startDate, String endDate) {
            // 模型只提供日期范围；员工身份仍由后端注入的 toolToken 决定。
            return call("query_attendance", Map.of("startDate", LocalDate.parse(startDate).toString(),
                    "endDate", LocalDate.parse(endDate).toString()));
        }

        @Tool(name = "query_leave_policy", description = "查询请假制度")
        public ToolResult<Object> queryLeavePolicy(String question) {
            // 制度查询最终会走 HrMcpToolService，当前配置下可复用 Dify Workflow/RAG。
            return call("query_leave_policy", Map.of("question", question));
        }

        @Tool(name = "create_leave_pending", description = "创建待用户确认的请假申请，绝不会直接提交")
        public ToolResult<Object> createLeavePending() {
            // 使用用户原始消息，避免模型改写参数时丢失日期、类型或原因。
            return call("create_leave_pending", Map.of("message", originalUserMessage));
        }

        @Tool(name = "confirm_leave_apply", description = "仅在用户明确确认后，确认并提交当前待确认请假申请")
        public ToolResult<Object> confirmLeaveApply() {
            PendingLeaveApplyDTO pending = currentPending();
            return call("confirm_leave_apply", Map.of(
                    "pendingId", pending.pendingId(),
                    "expectedVersion", pending.version(),
                    // 已提交请求复用原幂等键，确保响应丢失后的重试不会重复提交。
                    "idempotencyKey", "SUBMITTED".equals(pending.status())
                            ? pending.idempotencyKey()
                            : UUID.randomUUID().toString()
            ));
        }

        @Tool(name = "cancel_pending", description = "取消当前待确认请假申请")
        public ToolResult<Object> cancelPending() {
            PendingLeaveApplyDTO pending = currentPending();
            return call("cancel_pending", Map.of(
                    "pendingId", pending.pendingId(),
                    "expectedVersion", pending.version()
            ));
        }

        private PendingLeaveApplyDTO currentPending() {
            // 确认和取消必须基于同一 userId + sessionId 下已经存在的 pending。
            return memoryService.getPendingLeave(user.userId(), sessionId)
                    .orElseThrow(() -> new BusinessException("PENDING_NOT_FOUND", "当前没有待确认申请"));
        }

        private ToolResult<Object> call(String toolName, Map<String, Object> modelArguments) {
            /*
             * 所有模型发起的 Tool 调用都会经过这里：
             * 1. callGuard 控制单次请求内的调用次数和重复调用；
             * 2. 注入后端可信字段；
             * 3. 通过 HrMcpCaller 访问 MCP Server；
             * 4. 记录观测面板和脱敏日志。
             */
            callGuard.check(toolName);
            AgentToolCallLogger.ToolCallLogContext logContext = toolCallLogger.start(
                    sessionId, toolName, callGuard.callCount(), modelArguments
            );
            Map<String, Object> secured = new LinkedHashMap<>(modelArguments);
            // 安全字段只能由可信后端注入，禁止模型自行指定调用身份和会话边界。
            secured.put("toolToken", token);
            secured.put("sessionId", sessionId);
            long startedAtNanos = System.nanoTime();
            try {
                ToolResult<Object> result = hrMcpCaller.call(toolName, secured);
                toolResults.put(toolName, result);
                observeTool(toolName, modelArguments, result, elapsedMillis(startedAtNanos));
                toolCallLogger.success(logContext, result);
                return result;
            } catch (RuntimeException exception) {
                observeToolFailure(toolName, modelArguments, exception, elapsedMillis(startedAtNanos));
                toolCallLogger.failure(logContext, exception);
                throw exception;
            }
        }

        private void observeTool(
                String toolName,
                Map<String, Object> modelArguments,
                ToolResult<Object> result,
                long durationMs
        ) {
            observationBuilder.addToolCall(modelStep, new AgentToolObservation(
                    toolName,
                    result.success() ? AgentObservationStatus.SUCCESS : AgentObservationStatus.FAILED,
                    durationMs,
                    result.traceId(),
                    observationSanitizer.sanitizeInput(toolName, modelArguments),
                    observationSanitizer.sanitizeResult(toolName, result),
                    observationSanitizer.evidenceSource(toolName, result),
                    result.success() ? null : result.code()
            ));
        }

        private void observeToolFailure(
                String toolName,
                Map<String, Object> modelArguments,
                RuntimeException exception,
                long durationMs
        ) {
            String errorCode = exception instanceof BusinessException businessException
                    ? businessException.getCode()
                    : "MCP_TOOL_CALL_FAILED";
            ToolResult<Object> failure = ToolResult.failure(null, errorCode, "工具调用失败");
            observationBuilder.addToolCall(modelStep, new AgentToolObservation(
                    toolName,
                    AgentObservationStatus.FAILED,
                    durationMs,
                    null,
                    observationSanitizer.sanitizeInput(toolName, modelArguments),
                    observationSanitizer.sanitizeResult(toolName, failure),
                    null,
                    errorCode
            ));
        }

        private long elapsedMillis(long startNanos) {
            return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
        }
    }
}
