package com.example.hrai.workflow.leave;

import com.example.hrai.dto.workflow.LeaveApplyWorkflowRequest;
import com.example.hrai.dto.workflow.LeaveApplyWorkflowResponse;
import com.example.hrai.service.AiWorkflowLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class LeaveApplyWorkflow {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final List<LeaveApplyWorkflowStep> steps;
    private final AiWorkflowLogService aiWorkflowLogService;
    private final ConcurrentMap<LocalDate, AtomicInteger> dailySequences = new ConcurrentHashMap<>();

    public LeaveApplyWorkflowResponse execute(LeaveApplyWorkflowRequest request) {
        LeaveApplyWorkflowContext context = new LeaveApplyWorkflowContext();
        context.setWorkflowId(generateWorkflowId(LocalDateTime.now()));
        context.setStartedAt(LocalDateTime.now());
        context.setEmployeeName(request.getEmployeeName());
        context.setMessage(request.getMessage());

        try {
            for (LeaveApplyWorkflowStep step : steps) {
                if (context.isStopped()) {
                    break;
                }
                step.execute(context);
            }
            return new LeaveApplyWorkflowResponse(
                    context.getWorkflowId(),
                    context.getIntent(),
                    context.getStatus(),
                    context.getSteps(),
                    context.getResult()
            );
        } finally {
            aiWorkflowLogService.saveLeaveApplyRun(context);
        }
    }

    private String generateWorkflowId(LocalDateTime dateTime) {
        int sequence = dailySequences
                .computeIfAbsent(dateTime.toLocalDate(), ignored -> new AtomicInteger(0))
                .incrementAndGet();
        return "WF" + dateTime.format(DATE_TIME_FORMATTER) + String.format("%04d", sequence);
    }
}
