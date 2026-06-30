package com.example.hrai.ai.mcp.dto;

import java.time.YearMonth;

/**
 * query_salary 工资明细查询请求。
 *
 * @param toolToken MCP Tool 短期令牌，用于解析当前员工和权限 scope
 * @param salaryMonth 工资月份，ISO-8601 yyyy-MM 格式
 */
public record QuerySalaryMcpRequest(
        String toolToken,
        YearMonth salaryMonth
) {
}
