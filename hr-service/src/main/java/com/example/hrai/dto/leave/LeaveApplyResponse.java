package com.example.hrai.dto.leave;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveApplyResponse {

    private String applyNo;
    private String status;
    private String message;
}
