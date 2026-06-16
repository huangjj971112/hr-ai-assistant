package com.example.hrai.ai.multiagent;

/**
 * 薪酬影响判断级别。
 */
public enum SalaryImpactLevel {
    /** 制度明确显示通常不影响工资。 */
    NO_IMPACT,
    /** 制度或考勤事实显示可能影响，但需要 HR 或更细规则确认。 */
    POSSIBLE_IMPACT,
    /** 制度明确显示会影响工资。预留给后续薪酬接口或更明确规则使用。 */
    IMPACT,
    /** 当前制度依据不足，不能给确定结论。 */
    UNKNOWN
}
