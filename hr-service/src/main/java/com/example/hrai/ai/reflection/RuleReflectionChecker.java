package com.example.hrai.ai.reflection;

import com.example.hrai.ai.observation.AgentObservationSnapshot;
import com.example.hrai.ai.observation.AgentObservationStep;
import com.example.hrai.ai.observation.AgentObservationStatus;
import com.example.hrai.ai.observation.AgentToolObservation;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 第一层确定性反思检查。
 *
 * <p>这里不调用模型，只处理可以稳定判断的问题，例如超时、权限失败、
 * 5xx、空结果和明显非法的请假时间。</p>
 */
@Component
public class RuleReflectionChecker {

    /**
     * 遍历本次请求中所有 Agent/Tool 观测，一旦发现确定性问题就立即返回。
     *
     * <p>这里采用“第一个硬问题优先”的策略：例如某个 Tool 已经 403，
     * 就没有必要继续让 LLM 判断答案是否完整。</p>
     */
    public ReflectionResult check(ReflectionContext context) {
        AgentObservationSnapshot observation = context == null ? null : context.executedAgents();
        if (observation == null || observation.steps().isEmpty()) {
            return ReflectionResult.pass("无工具观测，规则反思放行");
        }
        for (AgentObservationStep step : observation.steps()) {
            for (AgentToolObservation tool : step.toolCalls()) {
                Optional<ReflectionResult> result = checkTool(tool, context.finalAnswer());
                if (result.isPresent()) {
                    return result.get();
                }
            }
        }
        return ReflectionResult.pass("规则反思未发现确定性问题");
    }

    private Optional<ReflectionResult> checkTool(AgentToolObservation tool, String finalAnswer) {
        /*
         * errorCode 来自 MCP Tool 或模型 Tool 调用观测。
         * 规则层只识别稳定错误，不尝试理解自然语言回答。
         */
        String errorCode = tool.errorCode() == null ? "" : tool.errorCode();
        if (containsIgnoreCase(errorCode, "TIMEOUT")) {
            return Optional.of(ReflectionResult.retry("工具调用超时，建议重试一次"));
        }
        if (is5xx(errorCode) || is5xx(tool.resultSummary())) {
            return Optional.of(ReflectionResult.retry("工具返回 5xx，建议重试一次"));
        }
        if ("FORBIDDEN".equals(errorCode) || "403".equals(errorCode)) {
            return Optional.of(ReflectionResult.fail("工具返回 403/FORBIDDEN", "您没有权限执行该操作。"));
        }
        if (hasInvalidLeaveRange(tool.inputSummary()) || hasInvalidLeaveRange(tool.resultSummary())) {
            return Optional.of(ReflectionResult.askUser(
                    "请假开始时间晚于结束时间",
                    "请假开始时间不能晚于结束时间，请重新说明请假时间。"
            ));
        }
        if (tool.status() == AgentObservationStatus.SUCCESS && isEmptyData(tool) && !hasUsableFinalAnswer(finalAnswer)) {
            /*
             * Tool 成功但没有任何可用数据，并且最终答案也没有形成有效结论时，才向用户追问。
             * Multi-Agent 场景里可能出现某个 Tool 为空，但 SalaryAgent 已经基于制度给出保守判断；
             * 这时不应该用规则层的 ASK_USER 覆盖已有答案。
             */
            return Optional.of(ReflectionResult.askUser(
                    "工具返回空数据",
                    "没有查询到足够的信息，请补充更明确的时间范围或问题。"
            ));
        }
        return Optional.empty();
    }

    private boolean isEmptyData(AgentToolObservation tool) {
        Map<String, Object> result = tool.resultSummary();
        if (result.isEmpty() && tool.evidenceSource() == null) {
            return true;
        }
        Object recordCount = result.get("recordCount");
        if (recordCount instanceof Number number) {
            return number.intValue() == 0;
        }
        Object answer = result.get("answer");
        return answer instanceof String text && text.isBlank();
    }

    private boolean hasUsableFinalAnswer(String finalAnswer) {
        if (finalAnswer == null || finalAnswer.isBlank()) {
            return false;
        }
        String answer = finalAnswer.trim();
        return !(answer.contains("没有查询到足够的信息") || answer.contains("请补充更明确"));
    }

    private boolean hasInvalidLeaveRange(Map<String, Object> values) {
        /*
         * 兼容两类字段：
         * - startTime/endTime：请假申请时间
         * - startDate/endDate：考勤或日期范围查询
         */
        LocalDateTime startTime = dateTime(values.get("startTime"));
        LocalDateTime endTime = dateTime(values.get("endTime"));
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            return true;
        }
        LocalDate startDate = date(values.get("startDate"));
        LocalDate endDate = date(values.get("endDate"));
        return startDate != null && endDate != null && startDate.isAfter(endDate);
    }

    private boolean is5xx(String value) {
        return value.startsWith("5") || value.startsWith("HTTP_5") || containsIgnoreCase(value, "5xx");
    }

    private boolean is5xx(Map<String, Object> values) {
        Object status = values.get("status");
        return status != null && is5xx(String.valueOf(status));
    }

    private boolean containsIgnoreCase(String text, String fragment) {
        return text != null && text.toLowerCase().contains(fragment.toLowerCase());
    }

    private LocalDateTime dateTime(Object value) {
        // 观测摘要里可能是 LocalDateTime，也可能已经被序列化成字符串。
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return LocalDateTime.parse(text.replace(" ", "T"));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private LocalDate date(Object value) {
        // 日期范围检查只需要 LocalDate 粒度。
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return LocalDate.parse(text);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }
}
