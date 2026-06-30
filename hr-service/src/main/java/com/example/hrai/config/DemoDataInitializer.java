package com.example.hrai.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hrai.entity.AttendanceRecord;
import com.example.hrai.entity.AttendanceStatus;
import com.example.hrai.entity.Candidate;
import com.example.hrai.entity.EmployeeLeaveBalance;
import com.example.hrai.entity.LeaveApplication;
import com.example.hrai.entity.LeaveStatus;
import com.example.hrai.entity.LeaveType;
import com.example.hrai.entity.SalaryRecord;
import com.example.hrai.entity.UserAccount;
import com.example.hrai.entity.UserRole;
import com.example.hrai.repository.CandidateRepository;
import com.example.hrai.repository.AttendanceRecordRepository;
import com.example.hrai.repository.EmployeeLeaveBalanceRepository;
import com.example.hrai.repository.LeaveApplicationRepository;
import com.example.hrai.repository.SalaryRecordRepository;
import com.example.hrai.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final EmployeeLeaveBalanceRepository employeeLeaveBalanceRepository;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final SalaryRecordRepository salaryRecordRepository;
    private final CandidateRepository candidateRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        initLeaveBalances();
        initLeaveApplications();
        initAttendanceRecords();
        initSalaryRecords();
        initCandidates();
        initUsers();
    }

    private void initSalaryRecords() {
        if (salaryRecordRepository.selectCount(new LambdaQueryWrapper<>()) > 0) {
            return;
        }
        List.of(
                new SalaryRecord(null, "张三", "2026-06",
                        new BigDecimal("4000.00"),
                        new BigDecimal("500.00"),
                        new BigDecimal("300.00"),
                        new BigDecimal("200.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("4000.00"),
                        new BigDecimal("3000.00"),
                        "本月实发较应发少 1000，主要来自考勤扣款、社保、公积金"),
                new SalaryRecord(null, "李四", "2026-06",
                        new BigDecimal("8000.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("600.00"),
                        new BigDecimal("600.00"),
                        new BigDecimal("1000.00"),
                        new BigDecimal("9000.00"),
                        new BigDecimal("7800.00"),
                        "本月含绩效奖金 1000，扣除社保和公积金后发放")
        ).forEach(salaryRecordRepository::insert);
    }

    private void initAttendanceRecords() {
        if (attendanceRecordRepository.selectCount(new LambdaQueryWrapper<>()) > 0) {
            return;
        }
        List.of(
                new AttendanceRecord(null, "张三", LocalDate.of(2026, 6, 3),
                        LocalTime.of(8, 55), LocalTime.of(18, 5), AttendanceStatus.NORMAL, "正常出勤"),
                new AttendanceRecord(null, "张三", LocalDate.of(2026, 6, 4),
                        LocalTime.of(9, 18), LocalTime.of(18, 2), AttendanceStatus.LATE, "迟到 18 分钟"),
                new AttendanceRecord(null, "张三", LocalDate.of(2026, 6, 5),
                        LocalTime.of(8, 58), LocalTime.of(17, 20), AttendanceStatus.EARLY_LEAVE, "早退 40 分钟"),
                new AttendanceRecord(null, "李四", LocalDate.of(2026, 6, 5),
                        LocalTime.of(8, 50), LocalTime.of(18, 10), AttendanceStatus.NORMAL, "正常出勤")
        ).forEach(attendanceRecordRepository::insert);
    }

    private void initLeaveApplications() {
        if (leaveApplicationRepository.selectCount(new LambdaQueryWrapper<>()) > 0) {
            return;
        }
        List.of(
                new LeaveApplication(null, "LV202601100001", "张三", LeaveType.ANNUAL,
                        LocalDateTime.of(2026, 1, 10, 9, 0),
                        LocalDateTime.of(2026, 1, 10, 18, 0),
                        "家庭事务", LeaveStatus.APPROVED,
                        LocalDateTime.of(2026, 1, 8, 10, 20)),
                new LeaveApplication(null, "LV202603180001", "张三", LeaveType.ANNUAL,
                        LocalDateTime.of(2026, 3, 18, 14, 0),
                        LocalDateTime.of(2026, 3, 18, 18, 0),
                        "个人事务", LeaveStatus.APPROVED,
                        LocalDateTime.of(2026, 3, 16, 15, 10)),
                new LeaveApplication(null, "LV202604070001", "张三", LeaveType.SICK,
                        LocalDateTime.of(2026, 4, 7, 9, 0),
                        LocalDateTime.of(2026, 4, 7, 18, 0),
                        "身体不适", LeaveStatus.APPROVED,
                        LocalDateTime.of(2026, 4, 7, 8, 30)),
                new LeaveApplication(null, "LV202602140001", "李四", LeaveType.ANNUAL,
                        LocalDateTime.of(2026, 2, 14, 9, 0),
                        LocalDateTime.of(2026, 2, 14, 18, 0),
                        "个人事务", LeaveStatus.APPROVED,
                        LocalDateTime.of(2026, 2, 10, 11, 0))
        ).forEach(leaveApplicationRepository::insert);
    }

    private void initLeaveBalances() {
        if (employeeLeaveBalanceRepository.selectCount(null) > 0) {
            return;
        }
        List.of(
                new EmployeeLeaveBalance(null, "张三", 5, 3),
                new EmployeeLeaveBalance(null, "李四", 8, 2),
                new EmployeeLeaveBalance(null, "王五", 12, 5)
        ).forEach(employeeLeaveBalanceRepository::insert);
    }

    private void initCandidates() {
        if (candidateRepository.selectCount(new LambdaQueryWrapper<>()) > 0) {
            return;
        }
        List.of(
                new Candidate(null, "李四", "高级 Java 开发工程师", 6,
                        "Java,Spring Boot,Spring Cloud,MySQL,Redis,RAG",
                        "待初面", "13800000001", "lisi@example.com"),
                new Candidate(null, "赵六", "AI 应用工程师", 4,
                        "Java,Spring AI,LangChain,PostgreSQL,Vector Search",
                        "待复面", "13800000002", "zhaoliu@example.com"),
                new Candidate(null, "钱七", "HR SaaS 产品经理", 7,
                        "HR SaaS,招聘,绩效,数据分析",
                        "已入库", "13800000003", "qianqi@example.com")
        ).forEach(candidateRepository::insert);
    }

    private void initUsers() {
        // 不再用“用户表为空”作为唯一条件，避免已有张三时遗漏 HR 管理员演示账号。
        ensureDemoUser("zhangsan", "张三", UserRole.EMPLOYEE);
        ensureDemoUser("hr_admin", "HR管理员", UserRole.HR);
    }

    private void ensureDemoUser(String username, String employeeName, UserRole role) {
        Long count = userAccountRepository.selectCount(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username));
        if (count > 0) {
            return;
        }
        userAccountRepository.insert(new UserAccount(
                null,
                username,
                passwordEncoder.encode("123456"),
                employeeName,
                role,
                true
        ));
    }
}
