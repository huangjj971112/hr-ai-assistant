package com.example.hrai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hrai.dto.user.CreateUserRequest;
import com.example.hrai.dto.user.UserAccountResponse;
import com.example.hrai.entity.EmployeeLeaveBalance;
import com.example.hrai.entity.UserAccount;
import com.example.hrai.entity.UserRole;
import com.example.hrai.exception.BusinessException;
import com.example.hrai.repository.EmployeeLeaveBalanceRepository;
import com.example.hrai.repository.UserAccountRepository;
import com.example.hrai.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserAccountRepository userAccountRepository;
    private final EmployeeLeaveBalanceRepository employeeLeaveBalanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    /**
     * 查询当前系统账号列表，只返回学习和管理需要的安全字段，不暴露 passwordHash。
     */
    public List<UserAccountResponse> listUsers() {
        requireHr();
        return userAccountRepository.selectList(new LambdaQueryWrapper<UserAccount>()
                        .orderByAsc(UserAccount::getId))
                .stream()
                .map(user -> toResponse(user, "SKIPPED", ""))
                .toList();
    }

    /**
     * 由 HR 创建一个可登录账号：先校验唯一账号和员工绑定关系，再按需创建员工假期档案。
     */
    @Transactional
    public UserAccountResponse createUser(CreateUserRequest request) {
        requireHr();
        String username = request.getUsername().trim();
        String employeeName = request.getEmployeeName().trim();
        if (existsUsername(username)) {
            throw new BusinessException("USER_ALREADY_EXISTS", "账号已存在：" + username);
        }
        if (request.getRole() == UserRole.EMPLOYEE && existsEmployeeAccount(employeeName)) {
            throw new BusinessException("EMPLOYEE_ACCOUNT_ALREADY_BOUND", "该员工已绑定普通员工账号：" + employeeName);
        }

        EmployeeProfileResult profileResult = prepareEmployeeProfile(request.getRole(), employeeName);

        UserAccount user = new UserAccount(
                null,
                username,
                passwordEncoder.encode(request.getPassword()),
                employeeName,
                request.getRole(),
                request.getEnabled() == null || request.getEnabled()
        );
        userAccountRepository.insert(user);
        return toResponse(user, profileResult.status(), profileResult.message());
    }

    private boolean existsUsername(String username) {
        return userAccountRepository.selectCount(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username)) > 0;
    }

    private boolean existsEmployeeAccount(String employeeName) {
        return userAccountRepository.selectCount(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getEmployeeName, employeeName)
                .eq(UserAccount::getRole, UserRole.EMPLOYEE)) > 0;
    }

    private EmployeeProfileResult prepareEmployeeProfile(UserRole role, String employeeName) {
        // HR 管理账号不强制创建假期余额档案；普通员工账号才参与员工自助数据归属。
        if (role != UserRole.EMPLOYEE) {
            return new EmployeeProfileResult("SKIPPED", "HR 账号无需创建员工假期余额档案");
        }
        if (existsEmployeeProfile(employeeName)) {
            return new EmployeeProfileResult("EXISTING", "已绑定已有员工档案：" + employeeName);
        }
        employeeLeaveBalanceRepository.insert(new EmployeeLeaveBalance(null, employeeName, 0, 0));
        return new EmployeeProfileResult("CREATED", "已自动创建员工假期余额档案，年假 0 天、病假 0 天");
    }

    private boolean existsEmployeeProfile(String employeeName) {
        return employeeLeaveBalanceRepository.selectCount(new LambdaQueryWrapper<EmployeeLeaveBalance>()
                .eq(EmployeeLeaveBalance::getEmployeeName, employeeName)) > 0;
    }

    private void requireHr() {
        if (currentUserService.currentUser().role() != UserRole.HR) {
            throw new BusinessException("FORBIDDEN", "只有 HR 账号可以管理用户");
        }
    }

    private UserAccountResponse toResponse(UserAccount user, String employeeProfileStatus, String message) {
        return new UserAccountResponse(
                user.getId(),
                user.getUsername(),
                user.getEmployeeName(),
                user.getRole(),
                user.getEnabled(),
                employeeProfileStatus,
                message
        );
    }

    private record EmployeeProfileResult(String status, String message) {
    }
}
