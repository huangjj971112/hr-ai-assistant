package com.example.hrai.dto.salary;

import java.math.BigDecimal;

/**
 * 工资明细查询结果。
 *
 * @param employeeName 员工姓名
 * @param salaryMonth 工资月份，yyyy-MM 格式
 * @param baseSalary 基本工资
 * @param attendanceDeduction 考勤扣款
 * @param socialSecurity 社保个人缴纳部分
 * @param housingFund 公积金个人缴纳部分
 * @param bonus 奖金或绩效加项
 * @param grossSalary 应发工资
 * @param netSalary 实发工资
 * @param totalDeduction 应发与实发的差额
 * @param remark 工资差异说明
 */
public record SalaryDetailResponse(
        String employeeName,
        String salaryMonth,
        BigDecimal baseSalary,
        BigDecimal attendanceDeduction,
        BigDecimal socialSecurity,
        BigDecimal housingFund,
        BigDecimal bonus,
        BigDecimal grossSalary,
        BigDecimal netSalary,
        BigDecimal totalDeduction,
        String remark
) {
}
