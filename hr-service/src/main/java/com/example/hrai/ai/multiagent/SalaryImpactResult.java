package com.example.hrai.ai.multiagent;

/**
 * SalaryAgent 的结构化输出。
 *
 * @param impactLevel 薪酬影响级别
 * @param summary 给最终回复使用的短结论
 * @param basis 判断依据，通常来自制度摘要
 * @param needsHumanConfirmation 是否建议 HR 人工确认
 */
public record SalaryImpactResult(
        SalaryImpactLevel impactLevel,
        String summary,
        String basis,
        boolean needsHumanConfirmation
) {
}
