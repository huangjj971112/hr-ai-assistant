package com.example.hrai.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hrai.entity.Candidate;
import com.example.hrai.entity.EmployeeLeaveBalance;
import com.example.hrai.entity.LeaveApplication;
import com.example.hrai.entity.LeaveStatus;
import com.example.hrai.entity.LeaveType;
import com.example.hrai.entity.UserAccount;
import com.example.hrai.entity.UserRole;
import com.example.hrai.repository.CandidateRepository;
import com.example.hrai.repository.EmployeeLeaveBalanceRepository;
import com.example.hrai.repository.LeaveApplicationRepository;
import com.example.hrai.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final EmployeeLeaveBalanceRepository employeeLeaveBalanceRepository;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final CandidateRepository candidateRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        initLeaveBalances();
        initLeaveApplications();
        initCandidates();
        initUsers();
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
        if (userAccountRepository.selectCount(new LambdaQueryWrapper<>()) > 0) {
            return;
        }
        List.of(
                new UserAccount(null, "zhangsan", passwordEncoder.encode("123456"), "张三", UserRole.EMPLOYEE, true),
                new UserAccount(null, "hr_admin", passwordEncoder.encode("123456"), "HR管理员", UserRole.HR, true)
        ).forEach(userAccountRepository::insert);
    }
}
