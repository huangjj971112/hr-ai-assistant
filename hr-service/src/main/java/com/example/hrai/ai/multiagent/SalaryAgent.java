package com.example.hrai.ai.multiagent;

import com.example.hrai.ai.mcp.client.HrMcpCaller;
import com.example.hrai.ai.security.ToolTokenService;
import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.dto.salary.SalaryDetailResponse;
import com.example.hrai.security.CurrentUserService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;

/**
 * 薪酬影响子 Agent。
 *
 * <p>工资明细类问题会通过 MCP query_salary 查询可信工资数据；
 * 请假、考勤导致的薪酬影响，则基于 PolicyAgent、AttendanceAgent 和 LeaveAgent 的结构化结果做保守判断。</p>
 */
@Component
public class SalaryAgent extends AbstractMcpSubAgent {

    private static final Set<String> SCOPES = Set.of("salary:detail:read");

    private final HrMcpCaller hrMcpCaller;
    private final Clock clock;

    public SalaryAgent(
            HrMcpCaller hrMcpCaller,
            CurrentUserService currentUserService,
            ToolTokenService toolTokenService,
            Clock clock
    ) {
        super(currentUserService, toolTokenService);
        this.hrMcpCaller = hrMcpCaller;
        this.clock = clock;
    }

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

        /*
         * 工资实发/应发不一致属于“工资明细核对”问题，不是单纯的制度解释问题。
         * 这类问题优先调用 query_salary；如果工具失败，再返回保守的人工确认提示。
         */
        if (isSalaryDetailQuestion(message)) {
            return querySalaryDetail(message);
        }

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

    private boolean isSalaryDetailQuestion(String message) {
        return containsAny(message, "工资条", "实发", "应发", "少发", "只发了", "发了三千", "发了3000");
    }

    private SalaryImpactResult querySalaryDetail(String message) {
        String salaryMonth = resolveSalaryMonth(message).toString();
        ToolResult<Object> result = hrMcpCaller.call("query_salary", Map.of(
                "toolToken", toolToken(SCOPES),
                "salaryMonth", salaryMonth
        ));
        if (!result.success()) {
            return new SalaryImpactResult(
                    SalaryImpactLevel.UNKNOWN,
                    "工资明细查询失败，暂时无法核对实发与应发差额。",
                    result.message(),
                    true,
                    result.traceId(),
                    salaryMonth,
                    null
            );
        }
        Object detail = result.data();
        BigDecimal grossSalary = money(detail, "grossSalary");
        BigDecimal netSalary = money(detail, "netSalary");
        BigDecimal totalDeduction = money(detail, "totalDeduction");
        BigDecimal attendanceDeduction = money(detail, "attendanceDeduction");
        BigDecimal socialSecurity = money(detail, "socialSecurity");
        BigDecimal housingFund = money(detail, "housingFund");
        String summary = "已查询 " + salaryMonth + " 工资明细：应发工资 " + grossSalary
                + " 元，实发工资 " + netSalary + " 元，差额 " + totalDeduction + " 元。";
        String basis = "差额构成：考勤扣款 " + attendanceDeduction
                + " 元，社保 " + socialSecurity
                + " 元，公积金 " + housingFund + " 元。";
        return new SalaryImpactResult(
                SalaryImpactLevel.POSSIBLE_IMPACT,
                summary,
                basis,
                true,
                result.traceId(),
                salaryMonth,
                detail
        );
    }

    private YearMonth resolveSalaryMonth(String message) {
        // 第一版先支持“这个月/本月”和默认当前月；后续可以扩展“上个月”“2026-05”等表达。
        return YearMonth.now(clock);
    }

    private BigDecimal money(Object detail, String fieldName) {
        Object value = null;
        if (detail instanceof Map<?, ?> map) {
            value = map.get(fieldName);
        } else if (detail instanceof SalaryDetailResponse response) {
            value = switch (fieldName) {
                case "grossSalary" -> response.grossSalary();
                case "netSalary" -> response.netSalary();
                case "totalDeduction" -> response.totalDeduction();
                case "attendanceDeduction" -> response.attendanceDeduction();
                case "socialSecurity" -> response.socialSecurity();
                case "housingFund" -> response.housingFund();
                default -> BigDecimal.ZERO;
            };
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            return new BigDecimal(text);
        }
        return BigDecimal.ZERO;
    }
}
