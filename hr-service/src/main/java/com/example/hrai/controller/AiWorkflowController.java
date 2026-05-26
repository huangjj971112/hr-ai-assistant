package com.example.hrai.controller;

import com.example.hrai.dto.workflow.LeaveApplyWorkflowRequest;
import com.example.hrai.dto.workflow.LeaveApplyWorkflowResponse;
import com.example.hrai.security.CurrentUserService;
import com.example.hrai.workflow.leave.LeaveApplyWorkflow;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/workflows")
public class AiWorkflowController {

    private final LeaveApplyWorkflow leaveApplyWorkflow;
    private final CurrentUserService currentUserService;

    @PostMapping("/leave/apply")
    public LeaveApplyWorkflowResponse applyLeave(@Valid @RequestBody LeaveApplyWorkflowRequest request) {
        request.setEmployeeName(currentUserService.currentUser().employeeName());
        return leaveApplyWorkflow.execute(request);
    }
}
