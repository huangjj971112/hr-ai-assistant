package com.example.hrai.security;

import com.example.hrai.entity.UserRole;
import com.example.hrai.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final HttpServletRequest request;

    public AuthenticatedUser currentUser() {
        AuthenticatedUser headerUser = currentUserFromGatewayHeaders();
        if (headerUser != null) {
            return headerUser;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new BusinessException("UNAUTHORIZED", "请先登录");
        }
        return user;
    }

    private AuthenticatedUser currentUserFromGatewayHeaders() {
        String userId = request.getHeader("X-User-Id");
        String username = request.getHeader("X-Username");
        String employeeName = request.getHeader("X-Employee-Name");
        String role = request.getHeader("X-User-Role");
        if (userId == null || username == null || employeeName == null || role == null) {
            return null;
        }
        return new AuthenticatedUser(
                Long.valueOf(userId),
                username,
                URLDecoder.decode(employeeName, StandardCharsets.UTF_8),
                UserRole.valueOf(role)
        );
    }
}
