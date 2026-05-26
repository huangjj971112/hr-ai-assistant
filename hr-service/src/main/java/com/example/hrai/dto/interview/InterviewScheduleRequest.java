package com.example.hrai.dto.interview;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InterviewScheduleRequest {

    @NotBlank
    private String candidateName;

    @NotBlank
    private String interviewerName;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime interviewTime;

    @NotBlank
    private String round;

    @NotBlank
    private String mode;
}
