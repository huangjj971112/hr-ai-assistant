package com.example.hrai.ai.service;

import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.dto.leave.LeaveBalanceResponse;

import java.util.Map;
import java.util.Optional;

/**
 * 对假期余额类回复做最后一层收口。
 *
 * <p>MCP 工具会返回年假和病假两个字段，这是给模型的完整业务数据；
 * 但面向用户时要尊重用户问题。如果用户只问年假，就不要额外展开病假，
 * 避免“答案正确但信息过多”。</p>
 */
class LeaveBalanceReplyPostProcessor {

    private static final String BALANCE_TOOL = "query_leave_balance";
    private static final String CREATE_PENDING_TOOL = "create_leave_pending";

    String refine(String userMessage, String modelReply, Map<String, ToolResult<Object>> toolResults) {
        if (hasSuccessfulPending(toolResults)) {
            return modelReply;
        }

        ToolResult<Object> balanceResult = toolResults.get(BALANCE_TOOL);
        if (balanceResult == null || !balanceResult.success()) {
            return modelReply;
        }

        Balance balance = Balance.from(balanceResult.data()).orElse(null);
        if (balance == null) {
            return modelReply;
        }

        boolean asksAnnual = userMessage.contains("年假");
        boolean asksSick = userMessage.contains("病假");
        if (asksAnnual && !asksSick) {
            return "您的年假余额为 " + balance.annualLeaveBalance() + " 天。";
        }
        if (asksSick && !asksAnnual) {
            return "您的病假余额为 " + balance.sickLeaveBalance() + " 天。";
        }
        return modelReply;
    }

    private boolean hasSuccessfulPending(Map<String, ToolResult<Object>> toolResults) {
        ToolResult<Object> pendingResult = toolResults.get(CREATE_PENDING_TOOL);
        return pendingResult != null && pendingResult.success();
    }

    private record Balance(int annualLeaveBalance, int sickLeaveBalance) {

        private static Optional<Balance> from(Object data) {
            if (data instanceof LeaveBalanceResponse response) {
                return Optional.of(new Balance(response.getAnnualLeaveBalance(), response.getSickLeaveBalance()));
            }
            if (data instanceof Map<?, ?> map) {
                Integer annual = integerValue(map.get("annualLeaveBalance"));
                Integer sick = integerValue(map.get("sickLeaveBalance"));
                if (annual != null && sick != null) {
                    return Optional.of(new Balance(annual, sick));
                }
            }
            return Optional.empty();
        }

        private static Integer integerValue(Object value) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
    }
}
