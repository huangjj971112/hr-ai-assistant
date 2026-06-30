package com.example.hrai.service.ai.tools;

import com.example.hrai.dto.salary.SalaryDetailResponse;
import com.example.hrai.service.SalaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;

/**
 * 工资相关 Tool 适配层。
 *
 * <p>MCP 层通过这里调用真实工资 Service，保持“Tool 只编排，不直接查库”的边界。</p>
 */
@Service
@RequiredArgsConstructor
public class SalaryTools {

    private final SalaryService salaryService;

    /**
     * 查询员工指定月份工资明细。
     */
    public SalaryDetailResponse querySalaryDetail(String employeeName, YearMonth salaryMonth) {
        return salaryService.getSalaryDetail(employeeName, salaryMonth);
    }
}
