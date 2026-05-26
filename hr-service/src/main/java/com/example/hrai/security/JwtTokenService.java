package com.example.hrai.security;

import com.example.hrai.config.SecurityProperties;
import com.example.hrai.entity.UserRole;
import com.example.hrai.exception.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public String createToken(AuthenticatedUser user) {
        try {
            long now = Instant.now().getEpochSecond();
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", user.username());
            payload.put("uid", user.userId());
            payload.put("employeeName", user.employeeName());
            payload.put("role", user.role().name());
            payload.put("iat", now);
            payload.put("exp", now + securityProperties.getJwtExpirationSeconds());

            String headerPart = encodeJson(header);
            String payloadPart = encodeJson(payload);
            String signingInput = headerPart + "." + payloadPart;
            return signingInput + "." + sign(signingInput);
        } catch (Exception exception) {
            throw new BusinessException("TOKEN_CREATE_FAILED", "登录令牌生成失败");
        }
    }

    public AuthenticatedUser parseToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new BusinessException("INVALID_TOKEN", "登录令牌格式错误");
            }
            String signingInput = parts[0] + "." + parts[1];
            if (!sign(signingInput).equals(parts[2])) {
                throw new BusinessException("INVALID_TOKEN", "登录令牌签名无效");
            }

            Map<String, Object> payload = objectMapper.readValue(
                    URL_DECODER.decode(parts[1]),
                    new TypeReference<>() {
                    }
            );
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= exp) {
                throw new BusinessException("TOKEN_EXPIRED", "登录令牌已过期");
            }
            return new AuthenticatedUser(
                    ((Number) payload.get("uid")).longValue(),
                    (String) payload.get("sub"),
                    (String) payload.get("employeeName"),
                    UserRole.valueOf((String) payload.get("role"))
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("INVALID_TOKEN", "登录令牌解析失败");
        }
    }

    public long getExpirationSeconds() {
        return securityProperties.getJwtExpirationSeconds();
    }

    private String encodeJson(Map<String, Object> json) throws Exception {
        return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(json));
    }

    private String sign(String input) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(securityProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return URL_ENCODER.encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    }
}
