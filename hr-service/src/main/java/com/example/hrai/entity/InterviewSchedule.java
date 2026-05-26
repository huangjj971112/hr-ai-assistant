package com.example.hrai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("interview_schedule")
public class InterviewSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String interviewNo;

    private String candidateName;

    private String interviewerName;

    private LocalDateTime interviewTime;

    private String round;

    private String mode;

    private InterviewStatus status;

    private LocalDateTime createdAt;
}
