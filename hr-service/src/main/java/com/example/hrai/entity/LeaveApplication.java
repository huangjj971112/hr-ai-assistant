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
@TableName("leave_application")
public class LeaveApplication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String applyNo;

    private String employeeName;

    private LeaveType leaveType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String reason;

    private LeaveStatus status;

    private LocalDateTime createdAt;
}
