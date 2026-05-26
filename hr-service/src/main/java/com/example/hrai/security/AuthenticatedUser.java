package com.example.hrai.security;

import com.example.hrai.entity.UserRole;

public record AuthenticatedUser(
        Long userId,
        String username,
        String employeeName,
        UserRole role
) {
}
