package com.example.hrai.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String tokenType;
    private String accessToken;
    private Long expiresInSeconds;
    private String username;
    private String employeeName;
    private String role;
}
