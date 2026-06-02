package com.example.hrai.ai.controller;

import com.example.hrai.ai.dto.LeaveBalanceToolRequest;
import com.example.hrai.ai.dto.LeaveRecordsToolRequest;
import com.example.hrai.ai.service.AiToolService;
import com.example.hrai.dto.leave.LeaveApplicationResponse;
import com.example.hrai.dto.leave.LeaveBalanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/tools")
public class AiToolController {

    private final AiToolService aiToolService;

    @PostMapping("/leave/balance")
    public LeaveBalanceResponse queryLeaveBalance(@RequestBody LeaveBalanceToolRequest request) {
        return aiToolService.queryLeaveBalance(request);
    }

    @PostMapping("/leave/records")
    public List<LeaveApplicationResponse> queryLeaveRecords(@RequestBody LeaveRecordsToolRequest request) {
        return aiToolService.queryLeaveRecords(request);
    }
}
