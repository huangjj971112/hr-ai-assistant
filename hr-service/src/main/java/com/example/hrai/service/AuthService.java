package com.example.hrai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hrai.dto.auth.LoginRequest;
import com.example.hrai.dto.auth.LoginResponse;
import com.example.hrai.entity.UserAccount;
import com.example.hrai.exception.BusinessException;
import com.example.hrai.repository.UserAccountRepository;
import com.example.hrai.security.AuthenticatedUser;
import com.example.hrai.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public LoginResponse login(LoginRequest request) {
        UserAccount user = userAccountRepository.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getUsername, request.getUsername())
        );
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException("INVALID_CREDENTIALS", "用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("INVALID_CREDENTIALS", "用户名或密码错误");
        }

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getEmployeeName(),
                user.getRole()
        );
        return new LoginResponse(
                "Bearer",
                jwtTokenService.createToken(authenticatedUser),
                jwtTokenService.getExpirationSeconds(),
                authenticatedUser.username(),
                authenticatedUser.employeeName(),
                authenticatedUser.role().name()
        );
    }
}
