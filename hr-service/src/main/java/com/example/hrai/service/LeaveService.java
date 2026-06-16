package com.example.hrai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hrai.dto.leave.LeaveApplyRequest;
import com.example.hrai.dto.leave.LeaveApplyResponse;
import com.example.hrai.dto.leave.LeaveApplicationResponse;
import com.example.hrai.dto.leave.LeaveBalanceResponse;
import com.example.hrai.entity.EmployeeLeaveBalance;
import com.example.hrai.entity.LeaveApplication;
import com.example.hrai.entity.LeaveStatus;
import com.example.hrai.entity.LeaveType;
import com.example.hrai.exception.BusinessException;
import com.example.hrai.repository.EmployeeLeaveBalanceRepository;
import com.example.hrai.repository.LeaveApplicationRepository;
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
public class LeaveService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final EmployeeLeaveBalanceRepository employeeLeaveBalanceRepository;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final ConcurrentMap<LocalDate, AtomicInteger> dailyApplySequences = new ConcurrentHashMap<>();

    public LeaveBalanceResponse getBalance(String employeeName) {
        EmployeeLeaveBalance balance = findBalance(employeeName);
        return new LeaveBalanceResponse(
                balance.getEmployeeName(),
                balance.getAnnualLeaveBalance(),
                balance.getSickLeaveBalance()
        );
    }

    public boolean employeeExists(String employeeName) {
        return employeeLeaveBalanceRepository.selectCount(
                new LambdaQueryWrapper<EmployeeLeaveBalance>()
                        .eq(EmployeeLeaveBalance::getEmployeeName, employeeName)
        ) > 0;
    }

    public LeaveApplyResponse apply(LeaveApplyRequest request) {
        validateApply(request);

        LeaveApplication application = new LeaveApplication();
        application.setApplyNo(generateApplyNo(request.getStartTime().toLocalDate()));
        application.setEmployeeName(request.getEmployeeName());
        application.setLeaveType(request.getLeaveType());
        application.setStartTime(request.getStartTime());
        application.setEndTime(request.getEndTime());
        application.setReason(request.getReason());
        application.setStatus(LeaveStatus.PENDING);
        application.setCreatedAt(LocalDateTime.now());
        leaveApplicationRepository.insert(application);

        return new LeaveApplyResponse(application.getApplyNo(), application.getStatus().name(), "请假申请已提交，等待审批");
    }

    public void validateApply(LeaveApplyRequest request) {
        if (request.getLeaveType() == null
                || request.getStartTime() == null
                || request.getEndTime() == null
                || request.getReason() == null
                || request.getReason().isBlank()) {
            throw new BusinessException("INVALID_LEAVE_APPLY_PARAMS", "请假类型、开始时间、结束时间和原因不能为空");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException("INVALID_LEAVE_TIME", "请假结束时间必须晚于开始时间");
        }
        findBalance(request.getEmployeeName());
    }

    public List<LeaveApplicationResponse> listApplications(String employeeName, Integer year, LeaveType leaveType) {
        findBalance(employeeName);
        LambdaQueryWrapper<LeaveApplication> query = new LambdaQueryWrapper<LeaveApplication>()
                .eq(LeaveApplication::getEmployeeName, employeeName)
                .orderByDesc(LeaveApplication::getStartTime);
        if (year != null) {
            LocalDateTime start = LocalDate.of(year, 1, 1).atStartOfDay();
            LocalDateTime end = start.plusYears(1);
            query.ge(LeaveApplication::getStartTime, start)
                    .lt(LeaveApplication::getStartTime, end);
        }
        if (leaveType != null) {
            query.eq(LeaveApplication::getLeaveType, leaveType);
        }
        return leaveApplicationRepository.selectList(query)
                .stream()
                .map(this::toApplicationResponse)
                .toList();
    }

    private EmployeeLeaveBalance findBalance(String employeeName) {
        EmployeeLeaveBalance balance = employeeLeaveBalanceRepository.selectOne(
                new LambdaQueryWrapper<EmployeeLeaveBalance>()
                        .eq(EmployeeLeaveBalance::getEmployeeName, employeeName)
        );
        if (balance == null) {
            throw new BusinessException("EMPLOYEE_NOT_FOUND", "员工不存在：" + employeeName);
        }
        return balance;
    }

    private String generateApplyNo(LocalDate date) {
        String prefix = "LV" + date.format(DATE_FORMATTER);
        int sequence = dailyApplySequences
                .computeIfAbsent(date, ignored -> new AtomicInteger(findMaxApplySequence(prefix)))
                .incrementAndGet();
        return prefix + String.format("%04d", sequence);
    }

    private int findMaxApplySequence(String prefix) {
        return leaveApplicationRepository.selectList(
                        new LambdaQueryWrapper<LeaveApplication>()
                                .select(LeaveApplication::getApplyNo)
                                .likeRight(LeaveApplication::getApplyNo, prefix)
                ).stream()
                .map(LeaveApplication::getApplyNo)
                .map(applyNo -> parseApplySequence(applyNo, prefix))
                .max(Integer::compareTo)
                .orElse(0);
    }

    private int parseApplySequence(String applyNo, String prefix) {
        if (applyNo == null || !applyNo.startsWith(prefix)) {
            return 0;
        }
        try {
            return Integer.parseInt(applyNo.substring(prefix.length()));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private LeaveApplicationResponse toApplicationResponse(LeaveApplication application) {
        return new LeaveApplicationResponse(
                application.getApplyNo(),
                application.getEmployeeName(),
                application.getLeaveType().name(),
                application.getStartTime(),
                application.getEndTime(),
                application.getReason(),
                application.getStatus().name(),
                application.getCreatedAt()
        );
    }
}
