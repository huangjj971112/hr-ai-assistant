package com.example.hrai.controller;

import com.example.hrai.dto.leave.LeaveApplicationResponse;
import com.example.hrai.dto.employee.CurrentEmployeeResponse;
import com.example.hrai.dto.leave.LeaveBalanceResponse;
import com.example.hrai.entity.LeaveType;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import com.example.hrai.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/employee/me")
public class EmployeeMeController {

    private final CurrentUserService currentUserService;
    private final LeaveService leaveService;

    @GetMapping
    public CurrentEmployeeResponse currentEmployee() {
        AuthenticatedUser user = currentUserService.currentUser();
        return new CurrentEmployeeResponse(user.userId(), user.username(), user.employeeName(), user.role().name());
    }

    @GetMapping("/leave/balance")
    public LeaveBalanceResponse myLeaveBalance() {
        AuthenticatedUser user = currentUserService.currentUser();
        return leaveService.getBalance(user.employeeName());
    }

    @GetMapping("/leave/applications")
    public List<LeaveApplicationResponse> myLeaveApplications(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) LeaveType leaveType
    ) {
        AuthenticatedUser user = currentUserService.currentUser();
        return leaveService.listApplications(user.employeeName(), year, leaveType);
    }
}
