package com.example.hrai.ai.service;

import com.example.hrai.ai.mcp.dto.PendingLeaveMcpResult;
import com.example.hrai.ai.tool.ToolResult;
import com.example.hrai.dto.leave.LeaveBalanceResponse;
import com.example.hrai.entity.LeaveType;

import java.util.Map;
import java.util.Optional;

/**
 * 对“创建待确认请假申请”的模型回复做最后一层收口。
 *
 * <p>模型可能在成功调用 create_leave_pending 后，只总结了余额或制度信息；
 * 但用户最需要看到的是待确认申请本身。因此这里由后端根据 Tool 结果生成稳定话术，
 * 确保写操作继续停留在 pending + confirm 流程里。</p>
 */
class PendingLeaveReplyPostProcessor {

    private static final String CREATE_PENDING_TOOL = "create_leave_pending";
    private static final String BALANCE_TOOL = "query_leave_balance";

    String refine(String modelReply, Map<String, ToolResult<Object>> toolResults) {
        PendingLeave pending = pendingFrom(toolResults.get(CREATE_PENDING_TOOL)).orElse(null);
        if (pending == null) {
            return modelReply;
        }

        StringBuilder reply = new StringBuilder();
        reply.append("已为您创建一条待确认的请假申请：\n\n");
        reply.append("请假类型：").append(leaveTypeName(pending.leaveType())).append("\n");
        reply.append("请假时间：").append(pending.startTime()).append(" 至 ").append(pending.endTime()).append("\n");
        reply.append("状态：待确认\n");

        balanceLine(pending.leaveType(), toolResults.get(BALANCE_TOOL))
                .ifPresent(line -> reply.append("\n").append(line).append("\n"));

        reply.append("\n请回复“确认”提交申请，或回复“取消”放弃本次申请。");
        return reply.toString();
    }

    private Optional<PendingLeave> pendingFrom(ToolResult<Object> result) {
        if (result == null || !result.success()) {
            return Optional.empty();
        }
        Object data = result.data();
        if (data instanceof PendingLeaveMcpResult pending) {
            return Optional.of(new PendingLeave(pending.leaveType(), pending.startTime(), pending.endTime()));
        }
        if (data instanceof Map<?, ?> map) {
            LeaveType leaveType = leaveTypeValue(map.get("leaveType")).orElse(null);
            String startTime = stringValue(map.get("startTime")).orElse(null);
            String endTime = stringValue(map.get("endTime")).orElse(null);
            if (leaveType != null && startTime != null && endTime != null) {
                return Optional.of(new PendingLeave(leaveType, startTime, endTime));
            }
        }
        return Optional.empty();
    }

    private Optional<String> balanceLine(LeaveType leaveType, ToolResult<Object> result) {
        Balance balance = balanceFrom(result).orElse(null);
        if (balance == null) {
            return Optional.empty();
        }
        return switch (leaveType) {
            case ANNUAL -> Optional.of("您的年假余额为 " + balance.annualLeaveBalance() + " 天。");
            case SICK -> Optional.of("您的病假余额为 " + balance.sickLeaveBalance() + " 天。");
            case PERSONAL -> Optional.empty();
        };
    }

    private Optional<Balance> balanceFrom(ToolResult<Object> result) {
        if (result == null || !result.success()) {
            return Optional.empty();
        }
        Object data = result.data();
        if (data instanceof LeaveBalanceResponse response) {
            return Optional.of(new Balance(response.getAnnualLeaveBalance(), response.getSickLeaveBalance()));
        }
        if (data instanceof Map<?, ?> map) {
            Integer annual = integerValue(map.get("annualLeaveBalance")).orElse(null);
            Integer sick = integerValue(map.get("sickLeaveBalance")).orElse(null);
            if (annual != null && sick != null) {
                return Optional.of(new Balance(annual, sick));
            }
        }
        return Optional.empty();
    }

    private Optional<LeaveType> leaveTypeValue(Object value) {
        if (value instanceof LeaveType leaveType) {
            return Optional.of(leaveType);
        }
        return stringValue(value).flatMap(text -> {
            try {
                return Optional.of(LeaveType.valueOf(text));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        });
    }

    private Optional<Integer> integerValue(Object value) {
        if (value instanceof Number number) {
            return Optional.of(number.intValue());
        }
        return stringValue(value).flatMap(text -> {
            try {
                return Optional.of(Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        });
    }

    private Optional<String> stringValue(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String text = value.toString();
        return text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    private String leaveTypeName(LeaveType leaveType) {
        return switch (leaveType) {
            case ANNUAL -> "年假";
            case SICK -> "病假";
            case PERSONAL -> "事假";
        };
    }

    private record PendingLeave(LeaveType leaveType, String startTime, String endTime) {
    }

    private record Balance(int annualLeaveBalance, int sickLeaveBalance) {
    }
}
