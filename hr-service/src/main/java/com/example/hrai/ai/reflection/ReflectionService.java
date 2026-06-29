package com.example.hrai.ai.reflection;

import com.example.hrai.ai.observation.AgentObservationSnapshot;
import com.example.hrai.ai.observation.AgentObservationStatus;
import com.example.hrai.ai.observation.AgentObservationStep;
import com.example.hrai.ai.observation.AgentToolObservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Hybrid Reflection 统一入口。
 *
 * <p>执行顺序固定为：Rule Reflection -> LLM Reflection -> 合并最终动作。
 * 规则层发现 RETRY/ASK_USER/FAIL 等确定性问题时，不再调用 LLM，避免模型覆盖硬规则。</p>
 */
@Service
public class ReflectionService {

    private static final Logger log = LoggerFactory.getLogger(ReflectionService.class);
    private static final int MAX_RETRY = 1;

    private final RuleReflectionChecker ruleChecker;
    private final LlmReflectionChecker llmChecker;

    public ReflectionService(RuleReflectionChecker ruleChecker, LlmReflectionChecker llmChecker) {
        this.ruleChecker = ruleChecker;
        this.llmChecker = llmChecker;
    }

    /**
     * 对一次 Agent 执行结果做返回前检查。
     *
     * <p>这里不直接修改业务数据，也不重新调用 Tool；它只返回一个“建议动作”。
     * 调用方可以根据 ASK_USER/FAIL 覆盖回复文案，或者对 REPLAN/RETRY 先记录日志。</p>
     */
    public ReflectionResult reflect(ReflectionContext context) {
        String traceId = UUID.randomUUID().toString();
        /*
         * 先跑规则层。规则层适合处理确定性问题，例如权限失败、超时、明显无效时间。
         * 这些问题不需要模型判断，也不应该被模型“解释过去”。
         */
        ReflectionResult ruleResult = ruleChecker.check(context);
        String llmRawOutput = "";
        ReflectionResult finalResult = ruleResult;

        if (ruleResult.action() == ReflectionAction.PASS) {
            // 只有规则层放行后，才让 LLM 判断“结果是否完整、是否漏 Agent/Tool”等复杂问题。
            LlmReflectionChecker.LlmReflectionResult llmResult = llmChecker.check(context);
            llmRawOutput = llmResult.rawOutput();
            finalResult = normalize(llmResult.result());
            finalResult = downgradeUserReportedMismatch(context, finalResult);
        } else {
            finalResult = normalize(ruleResult);
        }

        log.info(
                "hybrid reflection traceId={} ruleReflectionAction={} llmReflectionRawOutput={} "
                        + "finalReflectionAction={} reason={}",
                traceId,
                ruleResult.action(),
                llmRawOutput,
                finalResult.action(),
                finalResult.reason()
        );
        if (finalResult.action() == ReflectionAction.REPLAN) {
            log.info("hybrid reflection replan requested traceId={} reason={}", traceId, finalResult.reason());
        }
        return finalResult.withObservation(traceId, ruleResult.action(), llmRawOutput);
    }

    private ReflectionResult normalize(ReflectionResult result) {
        /*
         * 所有 Reflection 输出都会经过这里做安全收口。
         * 目前主要约束 RETRY，避免后续接入自动重试时出现循环调用。
         */
        if (result.action() == ReflectionAction.RETRY && !allowRetry(result)) {
            return ReflectionResult.fail("Reflection RETRY 超过最大次数", "系统暂时无法完成操作，请稍后重试。");
        }
        return result;
    }

    private boolean allowRetry(ReflectionResult result) {
        /*
         * 第一阶段不在 ReflectionService 内真正重放 Tool 调用，这里只做最多 1 次的动作约束。
         * 后续如果接入自动重试，可以把重试计数放到 ReflectionContext。
         */
        return result.needRetry() && MAX_RETRY >= 1;
    }

    private ReflectionResult downgradeUserReportedMismatch(ReflectionContext context, ReflectionResult result) {
        /*
         * LLM Reflection 有时会把“用户自述”和“系统查询结果”当成两个同等事实源。
         * 例如用户问“我这周迟到了两次，会影响工资吗”，但 AttendanceAgent 查到 lateCount=0。
         * 这不是 Tool 之间的执行矛盾，而是“用户自述与系统记录不一致”。
         * 如果 Tool 都成功且最终答案已经说明依据，就不应该用 FAIL 覆盖答案。
         */
        if (result.action() != ReflectionAction.FAIL && result.action() != ReflectionAction.ASK_USER) {
            return result;
        }
        if (!hasUsableFinalAnswer(context) || hasFailedTool(context)) {
            return result;
        }
        String text = (result.reason() + " " + result.userMessage()).toLowerCase();
        boolean mentionsUserReport = text.contains("用户") && (text.contains("提到") || text.contains("自述"));
        boolean mentionsSystemResult = text.contains("查询结果") || text.contains("系统查询") || text.contains("记录显示");
        boolean mentionsMismatch = text.contains("矛盾") || text.contains("不一致");
        if (mentionsUserReport && mentionsSystemResult && mentionsMismatch) {
            return ReflectionResult.pass("用户自述与系统记录不一致，不视为 Agent 执行失败，保留最终答案");
        }
        return result;
    }

    private boolean hasUsableFinalAnswer(ReflectionContext context) {
        String answer = context == null ? "" : context.finalAnswer();
        return answer != null && !answer.isBlank()
                && !answer.contains("没有查询到足够的信息")
                && !answer.contains("请补充更明确");
    }

    private boolean hasFailedTool(ReflectionContext context) {
        AgentObservationSnapshot snapshot = context == null ? null : context.executedAgents();
        if (snapshot == null) {
            return false;
        }
        for (AgentObservationStep step : snapshot.steps()) {
            for (AgentToolObservation tool : step.toolCalls()) {
                if (tool.status() == AgentObservationStatus.FAILED
                        || tool.status() == AgentObservationStatus.PARTIAL) {
                    return true;
                }
            }
        }
        return false;
    }
}
