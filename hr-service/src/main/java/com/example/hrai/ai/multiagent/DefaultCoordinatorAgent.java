package com.example.hrai.ai.multiagent;

import com.example.hrai.dto.ai.AiChatResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * 默认 Multi-Agent 协调器。
 *
 * <p>执行顺序是：Planner 生成调度计划 -> 各子 Agent 返回结构化事实 ->
 * SalaryAgent 判断薪酬影响 -> Coordinator 汇总成最终回复。这里不写具体 HR 业务规则，
 * 也不直接调用确认提交类工具。</p>
 */
@Service
public class DefaultCoordinatorAgent implements CoordinatorAgent {

    private final AgentDispatchPlanner planner;
    private final PolicyAgent policyAgent;
    private final AttendanceAgent attendanceAgent;
    private final LeaveAgent leaveAgent;
    private final SalaryAgent salaryAgent;
    private final Clock clock;

    public DefaultCoordinatorAgent(
            AgentDispatchPlanner planner,
            PolicyAgent policyAgent,
            AttendanceAgent attendanceAgent,
            LeaveAgent leaveAgent,
            SalaryAgent salaryAgent,
            Clock clock
    ) {
        this.planner = planner;
        this.policyAgent = policyAgent;
        this.attendanceAgent = attendanceAgent;
        this.leaveAgent = leaveAgent;
        this.salaryAgent = salaryAgent;
        this.clock = clock;
    }

    @Override
    public boolean supports(String message) {
        return planner.supports(message);
    }

    @Override
    public AiChatResponse chat(String message, String sessionId) {
        AgentDispatchPlan plan = planner.plan(message);
        AgentInvocationContext context = new AgentInvocationContext(message, sessionId);

        /*
         * 每个子 Agent 只在计划需要时执行，避免不必要的 MCP/模型/业务调用。
         * 子 Agent 返回的是结构化结果，最终自然语言回复在 buildReply 中统一生成。
         */
        PolicyAgentResult policy = plan.needPolicy() ? policyAgent.query(context) : null;
        AttendanceAgentResult attendance = plan.needAttendance()
                ? attendanceAgent.query(context, resolveAttendanceRange(message))
                : null;
        LeaveAgentResult leave = plan.needLeave() ? leaveAgent.evaluate(context, plan) : null;
        SalaryImpactResult salary = plan.needSalary()
                ? salaryAgent.evaluate(new SalaryAgentInput(message, policy, attendance, leave))
                : null;

        MultiAgentResult result = new MultiAgentResult(plan, policy, attendance, leave, salary);
        return new AiChatResponse("MULTI_AGENT_RESPONSE", buildReply(result), result);
    }

    private AttendanceAgent.DateRange resolveAttendanceRange(String message) {
        LocalDate today = LocalDate.now(clock);
        if (message != null && (message.contains("这周") || message.contains("本周"))) {
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return new AttendanceAgent.DateRange(monday, today);
        }
        // 没有明确时间范围时，先用最近 7 天作为保守默认值。
        return new AttendanceAgent.DateRange(today.minusDays(6), today);
    }

    private String buildReply(MultiAgentResult result) {
        List<String> agents = new ArrayList<>();
        if (result.policy() != null) {
            agents.add("PolicyAgent");
        }
        if (result.attendance() != null) {
            agents.add("AttendanceAgent");
        }
        if (result.leave() != null) {
            agents.add("LeaveAgent");
        }
        if (result.salary() != null) {
            agents.add("SalaryAgent");
        }

        String salarySummary = result.salary() == null ? "暂未进行薪酬影响判断。" : result.salary().summary();
        String basis = result.salary() == null ? "" : "\n判断依据：" + result.salary().basis();
        return "已协调 " + String.join("、", agents) + " 进行分析。\n薪酬影响：" + salarySummary + basis;
    }
}
