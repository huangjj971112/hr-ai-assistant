package com.example.hrai.ai.security;

import com.example.hrai.entity.UserRole;

import java.util.Set;

public record ToolTokenContext(
        Long userId,
        String username,
        String employeeName,
        UserRole role,
        String tenantId,
        Set<String> scopes
) {

    public boolean hasScope(String scope) {
        return scopes != null && scopes.contains(scope);
    }
}
