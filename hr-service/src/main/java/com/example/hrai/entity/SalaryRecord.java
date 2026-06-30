package com.example.hrai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 员工月度工资明细。
 *
 * <p>这里存的是后端可信业务数据，LLM 只能通过 query_salary 工具读取，
 * 不能自行编造工资金额或扣款项。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("salary_record")
public class SalaryRecord {

    /** 主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 员工姓名，和登录账号绑定的 employeeName 对应。 */
    private String employeeName;

    /** 工资月份，固定 yyyy-MM 格式，例如 2026-06。 */
    private String salaryMonth;

    /** 基本工资。 */
    private BigDecimal baseSalary;

    /** 考勤扣款，例如迟到、早退、缺勤导致的扣款。 */
    private BigDecimal attendanceDeduction;

    /** 社保个人缴纳部分。 */
    private BigDecimal socialSecurity;

    /** 公积金个人缴纳部分。 */
    private BigDecimal housingFund;

    /** 奖金或绩效加项。 */
    private BigDecimal bonus;

    /** 应发工资，通常等于基本工资加奖金等应发项。 */
    private BigDecimal grossSalary;

    /** 实发工资，扣除社保、公积金、考勤扣款等之后的金额。 */
    private BigDecimal netSalary;

    /** 工资说明，用于解释本月差异来源。 */
    private String remark;
}
