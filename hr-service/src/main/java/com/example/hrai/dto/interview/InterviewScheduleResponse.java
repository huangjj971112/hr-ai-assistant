package com.example.hrai.dto.interview;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewScheduleResponse {

    private String interviewNo;
    private String status;
    private String message;
}
