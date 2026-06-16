package com.example.hrai.ai.multiagent;

import org.springframework.stereotype.Component;

/**
 * 薪酬影响子 Agent。
 *
 * <p>当前系统还没有独立薪酬接口，所以第一版只基于 PolicyAgent 的制度摘要、
 * AttendanceAgent 的考勤事实和 LeaveAgent 的假期事实做保守判断。它不会编造工资金额。</p>
 */
@Component
public class SalaryAgent {

    /**
     * 输出结构化薪酬影响级别。
     *
     * <p>如果制度依据不明确，返回 UNKNOWN 并提示需要 HR 确认，而不是猜测结论。</p>
     */
    public SalaryImpactResult evaluate(SalaryAgentInput input) {
        String message = input.message() == null ? "" : input.message();
        String policy = input.policy() == null || input.policy().policySummary() == null
                ? ""
                : input.policy().policySummary();

        if (message.contains("年假") && containsAny(policy, "带薪", "不扣工资", "不影响工资")) {
            return new SalaryImpactResult(
                    SalaryImpactLevel.NO_IMPACT,
                    "根据当前制度，年假正常审批通过后通常不影响工资。",
                    policy,
                    false
            );
        }
        if (containsAny(message, "迟到", "考勤") && containsAny(policy, "扣款", "扣工资", "影响绩效", "影响工资")) {
            return new SalaryImpactResult(
                    SalaryImpactLevel.POSSIBLE_IMPACT,
                    "根据当前制度和考勤信息，迟到可能影响工资或绩效。",
                    policy,
                    true
            );
        }
        if (message.contains("病假") && containsAny(policy, "扣款", "扣工资", "病假工资", "按比例")) {
            return new SalaryImpactResult(
                    SalaryImpactLevel.POSSIBLE_IMPACT,
                    "根据当前制度，病假可能按规则影响工资。",
                    policy,
                    true
            );
        }
        return new SalaryImpactResult(
                SalaryImpactLevel.UNKNOWN,
                "当前制度依据不足，无法确定是否影响工资，建议联系 HR 确认。",
                policy.isBlank() ? "未获得明确制度依据" : policy,
                true
        );
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
