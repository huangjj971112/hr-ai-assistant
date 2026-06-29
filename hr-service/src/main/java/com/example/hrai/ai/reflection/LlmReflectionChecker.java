package com.example.hrai.ai.reflection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 第二层 LLM 反思检查。
 *
 * <p>它只处理规则层无法稳定判断的问题：Planner 是否完成、Tool 结果是否足够、
 * 是否遗漏 Agent/Tool、多 Agent 结果是否矛盾、最终答案是否准确。</p>
 */
@Component
public class LlmReflectionChecker {

    private static final Logger log = LoggerFactory.getLogger(LlmReflectionChecker.class);

    private static final String SYSTEM_PROMPT = """
            你是 HR Agent 的 Reflection Checker。
            你只检查执行结果是否足以回答用户，不调用工具，不补充业务事实。
            需要判断：
            - Planner 计划是否完成
            - Tool 结果是否足够回答用户
            - 是否遗漏 Agent 或 Tool
            - 多 Agent 结果是否矛盾
            - 最终回答是否准确
            - 是否需要 REPLAN / ASK_USER / PASS
            注意：
            - Tool 返回结果是系统事实，用户原始问题只代表意图或用户自述。
            - 用户自述与 Tool 查询结果不一致时，不要直接判定为执行矛盾或 FAIL。
            - 如果最终答案已经说明“以系统记录/HR 审核为准”，应优先 PASS。
            - 只有 Tool 之间互相矛盾、关键 Tool 失败、或最终答案明显编造事实时，才使用 FAIL。
            只输出 JSON，不要 Markdown，不要代码块。
            action 只能是 PASS、RETRY、REPLAN、ASK_USER、FAIL。
            JSON 格式：
            {
              "action": "PASS",
              "reason": "执行结果完整，可以返回用户",
              "userMessage": "",
              "needRetry": false,
              "needReplan": false
            }
            """;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectMapper objectMapper;

    public LlmReflectionChecker(ObjectProvider<ChatModel> chatModelProvider, ObjectMapper objectMapper) {
        this.chatModelProvider = chatModelProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用大模型对复杂执行结果做二次检查。
     *
     * <p>为了不影响主业务链路，模型不可用、输出为空、JSON 非法或 action 非法时，
     * 这里都会默认 PASS，并把异常写入日志。</p>
     */
    public LlmReflectionResult check(ReflectionContext context) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return new LlmReflectionResult(ReflectionResult.pass("LLM Reflection 模型不可用，默认放行"), "");
        }
        String rawOutput = null;
        try {
            rawOutput = ChatClient.builder(chatModel).build().prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildPrompt(context))
                    .call()
                    .content();
            return new LlmReflectionResult(parse(rawOutput), rawOutput);
        } catch (RuntimeException exception) {
            log.warn("llm reflection parse failed, default PASS. rawOutput={}", rawOutput, exception);
            return new LlmReflectionResult(ReflectionResult.pass("LLM Reflection 输出解析失败，默认放行"), rawOutput);
        }
    }

    private String buildPrompt(ReflectionContext context) {
        try {
            /*
             * LLM Reflection 看到的是“执行快照”，不是原始数据库对象。
             * 这里保留 Planner、Agent、Tool、最终答案等判断所需信息。
             */
            return objectMapper.writeValueAsString(Map.of(
                    "originalMessage", safe(context.originalMessage()),
                    "plannerOutput", context.plannerOutput() == null ? Map.of() : context.plannerOutput(),
                    "executedAgents", context.executedAgents() == null ? Map.of() : context.executedAgents(),
                    "finalAnswer", safe(context.finalAnswer()),
                    "responseData", context.responseData() == null ? Map.of() : context.responseData()
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("无法序列化 Reflection 输入", exception);
        }
    }

    private ReflectionResult parse(String rawOutput) {
        // 只接受固定 JSON，不允许模型输出解释性 Markdown。
        if (!StringUtils.hasText(rawOutput)) {
            throw new IllegalArgumentException("LLM Reflection 输出为空");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(stripCodeFence(rawOutput));
        } catch (Exception exception) {
            throw new IllegalArgumentException("LLM Reflection JSON 解析失败", exception);
        }
        ReflectionAction action = ReflectionAction.valueOf(requiredText(root, "action"));
        /*
         * needRetry/needReplan 既可以由 JSON 显式给出，也可以由 action 推导。
         * 这样即使模型只填 action=REPLAN，后端仍能得到一致的布尔字段。
         */
        String reason = root.path("reason").asText("");
        String userMessage = root.path("userMessage").asText("");
        boolean needRetry = root.path("needRetry").asBoolean(action == ReflectionAction.RETRY);
        boolean needReplan = root.path("needReplan").asBoolean(action == ReflectionAction.REPLAN);
        return new ReflectionResult(action, reason, userMessage, needRetry, needReplan, "", null, "");
    }

    private String requiredText(JsonNode root, String fieldName) {
        String value = root.path(fieldName).asText("");
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("LLM Reflection 缺少字段：" + fieldName);
        }
        return value;
    }

    private String stripCodeFence(String rawOutput) {
        // 容错处理：如果模型偶尔包了 ```json 代码块，仍然尽量解析内部 JSON。
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record LlmReflectionResult(ReflectionResult result, String rawOutput) {
    }
}
