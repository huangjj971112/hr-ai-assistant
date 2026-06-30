package com.example.hrai.ai.multiagent;

import org.springframework.stereotype.Component;

/**
 * 轻量调度器，负责把用户问题转换成子 Agent 调用计划。
 *
 * <p>第一版故意使用可测试的规则判断，避免为了调度再调用一次模型造成额外费用。
 * 后续如果接入模型规划，也应该保持输出 {@link AgentDispatchPlan}，不要把业务判断写进 Coordinator。</p>
 */
@Component
public class AgentDispatchPlanner {

    /**
     * 根据问题关键词生成调度计划。
     *
     * <p>这里的规则只决定“该问谁”，不决定“业务结论是什么”。薪酬影响结论仍由
     * SalaryAgent 基于 Policy/Attendance/Leave 的结构化结果给出。</p>
     */
    public AgentDispatchPlan plan(String message) {
        String text = message == null ? "" : message;
        boolean salaryQuestion = containsAny(text,
                "工资", "扣工资", "薪资", "薪酬", "工资条", "实发", "应发", "少发", "只发了");
        boolean attendanceQuestion = containsAny(text, "迟到", "考勤", "打卡", "早退", "缺勤");
        boolean leaveQuestion = containsAny(text, "年假", "病假", "事假", "请假");
        boolean policyQuestion = salaryQuestion || containsAny(text, "制度", "规则", "政策", "会不会", "影响");
        boolean writeAction = containsAny(text, "帮我请", "申请", "提交") && !containsAny(text, "先不要提交", "不要提交");

        return new AgentDispatchPlan(
                policyQuestion,
                attendanceQuestion,
                leaveQuestion && !text.contains("病假，会不会"),
                salaryQuestion,
                writeAction,
                salaryQuestion ? "薪酬影响问题需要协调多个子 Agent" : "非薪酬复合问题"
        );
    }

    public boolean supports(String message) {
        AgentDispatchPlan plan = plan(message);
        return plan.needSalary() && (plan.needPolicy() || plan.needAttendance() || plan.needLeave());
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
