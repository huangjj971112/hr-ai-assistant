package com.example.hraigateway.security;

public record GatewayUser(
        Long userId,
        String username,
        String employeeName,
        String role
) {
}
