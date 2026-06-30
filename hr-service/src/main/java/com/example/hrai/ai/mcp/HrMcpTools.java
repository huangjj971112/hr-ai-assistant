package com.example.hrai.ai.mcp;

import com.example.hrai.ai.mcp.dto.CancelPendingMcpRequest;
import com.example.hrai.ai.mcp.dto.ConfirmLeaveApplyMcpRequest;
import com.example.hrai.ai.mcp.dto.CreateLeavePendingMcpRequest;
import com.example.hrai.ai.mcp.dto.QueryAttendanceMcpRequest;
import com.example.hrai.ai.mcp.dto.QueryLeaveBalanceMcpRequest;
import com.example.hrai.ai.mcp.dto.QueryLeavePolicyMcpRequest;
import com.example.hrai.ai.mcp.dto.QuerySalaryMcpRequest;
import com.example.hrai.ai.tool.ToolResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * MCP Server 对外暴露的六个 Tool 定义。
 *
 * <p>本类只负责协议暴露和 DTO 边界，不编写业务逻辑；
 * 所有校验、权限、审计和业务编排均委托给 {@link HrMcpToolService}。</p>
 */
@Component
public class HrMcpTools {

    private final HrMcpToolService service;

    public HrMcpTools(HrMcpToolService service) {
        this.service = service;
    }

    @Tool(name = "query_leave_balance", description = "查询工具令牌对应员工的假期余额")
    public ToolResult<?> queryLeaveBalance(QueryLeaveBalanceMcpRequest request) {
        return service.queryLeaveBalance(request);
    }

    @Tool(name = "query_attendance", description = "查询工具令牌对应员工指定日期范围内的考勤")
    public ToolResult<?> queryAttendance(QueryAttendanceMcpRequest request) {
        return service.queryAttendance(request);
    }

    @Tool(name = "query_leave_policy", description = "查询员工手册中的请假制度")
    public ToolResult<?> queryLeavePolicy(QueryLeavePolicyMcpRequest request) {
        return service.queryLeavePolicy(request);
    }

    @Tool(name = "query_salary", description = "查询工具令牌对应员工指定月份的工资明细")
    public ToolResult<?> querySalary(QuerySalaryMcpRequest request) {
        return service.querySalary(request);
    }

    @Tool(name = "create_leave_pending", description = "解析请假消息并创建待确认申请，不会直接提交")
    public ToolResult<?> createLeavePending(CreateLeavePendingMcpRequest request) {
        return service.createLeavePending(request);
    }

    @Tool(name = "confirm_leave_apply", description = "确认并提交同一员工同一会话中的待确认请假申请")
    public ToolResult<?> confirmLeaveApply(ConfirmLeaveApplyMcpRequest request) {
        return service.confirmLeaveApply(request);
    }

    @Tool(name = "cancel_pending", description = "取消同一员工同一会话中的待确认请假申请")
    public ToolResult<?> cancelPending(CancelPendingMcpRequest request) {
        return service.cancelPending(request);
    }
}
