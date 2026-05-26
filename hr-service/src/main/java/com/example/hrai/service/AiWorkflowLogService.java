package com.example.hrai.service;

import com.example.hrai.dto.workflow.WorkflowStepResponse;
import com.example.hrai.entity.AiWorkflowRun;
import com.example.hrai.entity.AiWorkflowStepLog;
import com.example.hrai.repository.AiWorkflowRunRepository;
import com.example.hrai.repository.AiWorkflowStepLogRepository;
import com.example.hrai.workflow.leave.LeaveApplyWorkflowContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiWorkflowLogService {

    private final AiWorkflowRunRepository aiWorkflowRunRepository;
    private final AiWorkflowStepLogRepository aiWorkflowStepLogRepository;

    @Transactional
    public void saveLeaveApplyRun(LeaveApplyWorkflowContext context) {
        AiWorkflowRun run = new AiWorkflowRun();
        run.setWorkflowId(context.getWorkflowId());
        run.setWorkflowType("LEAVE_APPLY");
        run.setEmployeeName(context.getEmployeeName());
        run.setMessage(context.getMessage());
        run.setIntent(context.getIntent());
        run.setStatus(context.getStatus());
        run.setResultSummary(context.getResult() == null ? null : context.getResult().toString());
        run.setStartedAt(context.getStartedAt());
        run.setFinishedAt(LocalDateTime.now());
        aiWorkflowRunRepository.insert(run);

        List<WorkflowStepResponse> steps = context.getSteps();
        for (int index = 0; index < steps.size(); index++) {
            WorkflowStepResponse step = steps.get(index);
            AiWorkflowStepLog stepLog = new AiWorkflowStepLog();
            stepLog.setWorkflowId(context.getWorkflowId());
            stepLog.setStepOrder(index + 1);
            stepLog.setStepName(step.getStepName());
            stepLog.setStatus(step.getStatus());
            stepLog.setMessage(step.getMessage());
            stepLog.setExecutedAt(step.getExecutedAt());
            aiWorkflowStepLogRepository.insert(stepLog);
        }
    }
}
