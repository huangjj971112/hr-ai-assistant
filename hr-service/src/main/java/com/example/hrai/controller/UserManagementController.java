package com.example.hrai.controller;

import com.example.hrai.dto.user.CreateUserRequest;
import com.example.hrai.dto.user.UserAccountResponse;
import com.example.hrai.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hr/users")
public class UserManagementController {

    private final UserManagementService userManagementService;

    /**
     * HR 查看当前账号列表，用于确认“谁能登录、是什么角色、是否启用”。
     */
    @GetMapping
    public List<UserAccountResponse> listUsers() {
        return userManagementService.listUsers();
    }

    /**
     * HR 创建新账号；创建成功后，新账号可以直接使用用户名和初始密码登录。
     */
    @PostMapping
    public UserAccountResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userManagementService.createUser(request);
    }
}
