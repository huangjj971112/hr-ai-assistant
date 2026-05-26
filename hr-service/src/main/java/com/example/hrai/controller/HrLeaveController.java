package com.example.hrai.controller;

import com.example.hrai.dto.leave.LeaveApplyRequest;
import com.example.hrai.dto.leave.LeaveApplyResponse;
import com.example.hrai.dto.leave.LeaveBalanceResponse;
import com.example.hrai.service.LeaveService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hr/leave")
public class HrLeaveController {

    private final LeaveService leaveService;

    @GetMapping("/balance")
    public LeaveBalanceResponse getBalance(@RequestParam @NotBlank String employeeName) {
        return leaveService.getBalance(employeeName);
    }

    @PostMapping("/apply")
    public LeaveApplyResponse apply(@Valid @RequestBody LeaveApplyRequest request) {
        return leaveService.apply(request);
    }
}
