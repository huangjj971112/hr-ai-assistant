package com.example.hrai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hrai.dto.salary.SalaryDetailResponse;
import com.example.hrai.entity.SalaryRecord;
import com.example.hrai.exception.BusinessException;
import com.example.hrai.repository.SalaryRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * 工资明细业务 Service。
 *
 * <p>这里只负责查询可信工资数据，不接触 LLM prompt，也不做自然语言判断。</p>
 */
@Service
@RequiredArgsConstructor
public class SalaryService {

    private final SalaryRecordRepository salaryRecordRepository;

    /**
     * 查询某员工某月工资明细。
     *
     * @param employeeName 员工姓名，由登录态或 Tool Token 决定
     * @param salaryMonth 工资月份
     * @return 工资明细
     */
    public SalaryDetailResponse getSalaryDetail(String employeeName, YearMonth salaryMonth) {
        if (employeeName == null || employeeName.isBlank() || salaryMonth == null) {
            throw new BusinessException("INVALID_SALARY_QUERY_PARAMS", "员工姓名和工资月份不能为空");
        }
        SalaryRecord record = salaryRecordRepository.selectOne(new LambdaQueryWrapper<SalaryRecord>()
                .eq(SalaryRecord::getEmployeeName, employeeName)
                .eq(SalaryRecord::getSalaryMonth, salaryMonth.toString()));
        if (record == null) {
            throw new BusinessException("SALARY_RECORD_NOT_FOUND", "未查询到工资明细：" + salaryMonth);
        }
        return toResponse(record);
    }

    private SalaryDetailResponse toResponse(SalaryRecord record) {
        BigDecimal totalDeduction = record.getGrossSalary().subtract(record.getNetSalary());
        return new SalaryDetailResponse(
                record.getEmployeeName(),
                record.getSalaryMonth(),
                record.getBaseSalary(),
                record.getAttendanceDeduction(),
                record.getSocialSecurity(),
                record.getHousingFund(),
                record.getBonus(),
                record.getGrossSalary(),
                record.getNetSalary(),
                totalDeduction,
                record.getRemark()
        );
    }
}
