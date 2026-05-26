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
@TableName("ai_workflow_run")
public class AiWorkflowRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String workflowId;

    private String workflowType;

    private String employeeName;

    private String message;

    private String intent;

    private String status;

    private String resultSummary;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
