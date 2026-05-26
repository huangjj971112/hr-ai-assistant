package com.example.hrai.dto.leave;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveBalanceResponse {

    private String employeeName;
    private int annualLeaveBalance;
    private int sickLeaveBalance;
}
