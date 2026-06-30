package com.example.hrai.ai.multiagent;

/**
 * SalaryAgent 的结构化输出。
 *
 * @param impactLevel 薪酬影响级别
 * @param summary 给最终回复使用的短结论
 * @param basis 判断依据，通常来自制度摘要
 * @param needsHumanConfirmation 是否建议 HR 人工确认
 * @param traceId query_salary 等工具调用 traceId；没有调用工具时为空
 * @param salaryMonth 已查询的工资月份，yyyy-MM 格式；没有调用工资工具时为空
 * @param salaryDetail 工资明细原始结构化数据；没有调用工资工具时为空
 */
public record SalaryImpactResult(
        SalaryImpactLevel impactLevel,
        String summary,
        String basis,
        boolean needsHumanConfirmation,
        String traceId,
        String salaryMonth,
        Object salaryDetail
) {
    public SalaryImpactResult(
            SalaryImpactLevel impactLevel,
            String summary,
            String basis,
            boolean needsHumanConfirmation
    ) {
        this(impactLevel, summary, basis, needsHumanConfirmation, null, null, null);
    }
}
