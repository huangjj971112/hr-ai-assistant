package com.example.hrai.ai.security;

import com.example.hrai.config.SecurityProperties;
import com.example.hrai.entity.UserRole;
import com.example.hrai.exception.BusinessException;
import com.example.hrai.security.AuthenticatedUser;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ToolTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_TYPE = "tool";
    private static final long DEFAULT_TTL_SECONDS = 300;
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public String createToken(AuthenticatedUser user, String tenantId, Set<String> scopes) {
        try {
            long now = Instant.now().getEpochSecond();
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("typ", TOKEN_TYPE);
            payload.put("sub", user.username());
            payload.put("uid", user.userId());
            payload.put("employeeName", user.employeeName());
            payload.put("role", user.role().name());
            payload.put("tenantId", tenantId);
            payload.put("scopes", scopes == null ? List.of() : scopes.stream().sorted().toList());
            payload.put("iat", now);
            payload.put("exp", now + DEFAULT_TTL_SECONDS);

            String headerPart = encodeJson(header);
            String payloadPart = encodeJson(payload);
            String signingInput = headerPart + "." + payloadPart;
            return signingInput + "." + sign(signingInput);
        } catch (Exception exception) {
            throw new BusinessException("TOOL_TOKEN_CREATE_FAILED", "工具调用令牌生成失败");
        }
    }

    public ToolTokenContext parseToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new BusinessException("INVALID_TOOL_TOKEN", "工具调用令牌格式错误");
            }
            String signingInput = parts[0] + "." + parts[1];
            if (!sign(signingInput).equals(parts[2])) {
                throw new BusinessException("INVALID_TOOL_TOKEN", "工具调用令牌签名无效");
            }

            Map<String, Object> payload = objectMapper.readValue(
                    URL_DECODER.decode(parts[1]),
                    new TypeReference<>() {
                    }
            );
            if (!TOKEN_TYPE.equals(payload.get("typ"))) {
                throw new BusinessException("INVALID_TOOL_TOKEN", "不是工具调用令牌");
            }
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= exp) {
                throw new BusinessException("TOOL_TOKEN_EXPIRED", "工具调用令牌已过期");
            }
            return new ToolTokenContext(
                    ((Number) payload.get("uid")).longValue(),
                    (String) payload.get("sub"),
                    (String) payload.get("employeeName"),
                    UserRole.valueOf((String) payload.get("role")),
                    (String) payload.get("tenantId"),
                    parseScopes(payload.get("scopes"))
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("INVALID_TOOL_TOKEN", "工具调用令牌解析失败");
        }
    }

    private Set<String> parseScopes(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
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
