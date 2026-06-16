package com.example.hrai.ai.multiagent;

import java.time.LocalDate;

/**
 * 考勤子 Agent。
 *
 * <p>只负责查询并结构化考勤事实，例如迟到次数；不判断工资是否扣减，
 * 工资影响统一交给 SalaryAgent 汇总判断。</p>
 */
public interface AttendanceAgent {

    AttendanceAgentResult query(AgentInvocationContext context, DateRange dateRange);

    /**
     * Coordinator 解析出的考勤查询范围。
     */
    record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}
