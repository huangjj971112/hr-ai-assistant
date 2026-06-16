package com.example.hrai.controller;

import com.example.hrai.dto.attendance.AttendanceRecordResponse;
import com.example.hrai.dto.employee.EmployeeLeaveApplyRequest;
import com.example.hrai.dto.leave.LeaveApplicationResponse;
import com.example.hrai.dto.employee.CurrentEmployeeResponse;
import com.example.hrai.dto.leave.LeaveApplyRequest;
import com.example.hrai.dto.leave.LeaveApplyResponse;
import com.example.hrai.dto.leave.LeaveBalanceResponse;
import com.example.hrai.entity.LeaveType;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.CurrentUserService;
import com.example.hrai.service.AttendanceService;
import com.example.hrai.service.LeaveService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/employee/me")
public class EmployeeMeController {

    private final CurrentUserService currentUserService;
    private final LeaveService leaveService;
    private final AttendanceService attendanceService;

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

    @PostMapping("/leave/apply")
    public LeaveApplyResponse applyMyLeave(@Valid @RequestBody EmployeeLeaveApplyRequest request) {
        AuthenticatedUser user = currentUserService.currentUser();
        LeaveApplyRequest applyRequest = new LeaveApplyRequest();
        applyRequest.setEmployeeName(user.employeeName());
        applyRequest.setLeaveType(request.getLeaveType());
        applyRequest.setStartTime(request.getStartTime());
        applyRequest.setEndTime(request.getEndTime());
        applyRequest.setReason(request.getReason());
        return leaveService.apply(applyRequest);
    }

    @GetMapping("/attendance/records")
    public List<AttendanceRecordResponse> myAttendanceRecords(
            @RequestParam(required = false) LocalDate attendanceDate,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        AuthenticatedUser user = currentUserService.currentUser();
        if (startDate == null && endDate == null && attendanceDate != null) {
            startDate = attendanceDate;
            endDate = attendanceDate;
        }
        return attendanceService.listRecords(user.employeeName(), startDate, endDate);
    }
}
