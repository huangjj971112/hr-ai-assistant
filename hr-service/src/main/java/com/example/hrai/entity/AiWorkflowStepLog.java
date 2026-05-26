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
@TableName("ai_workflow_step_log")
public class AiWorkflowStepLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String workflowId;

    private Integer stepOrder;

    private String stepName;

    private String status;

    private String message;

    private LocalDateTime executedAt;
}
