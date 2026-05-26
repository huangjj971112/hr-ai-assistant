package com.example.hrai.dto.employee;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentEmployeeResponse {

    private Long userId;
    private String username;
    private String employeeName;
    private String role;
}
