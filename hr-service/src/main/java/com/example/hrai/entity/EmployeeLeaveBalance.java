package com.example.hrai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("employee_leave_balance")
public class EmployeeLeaveBalance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String employeeName;

    private int annualLeaveBalance;

    private int sickLeaveBalance;
}
