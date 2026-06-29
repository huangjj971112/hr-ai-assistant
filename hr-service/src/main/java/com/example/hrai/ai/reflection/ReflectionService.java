package com.example.hrai.ai.reflection;

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
        return finalResult;
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
}
