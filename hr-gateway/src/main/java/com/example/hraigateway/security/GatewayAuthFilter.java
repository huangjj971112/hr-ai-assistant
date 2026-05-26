package com.example.hraigateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class GatewayAuthFilter extends OncePerRequestFilter {

    public static final String USER_REQUEST_ATTRIBUTE = "gatewayUser";

    private final GatewayJwtService gatewayJwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        GatewayUser user = parseUser(request, response);
        if (user == null) {
            return;
        }
        if (!allowed(path, user)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"code\":\"FORBIDDEN\",\"message\":\"无权限访问该接口\"}");
            return;
        }

        request.setAttribute(USER_REQUEST_ATTRIBUTE, user);
        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/auth/") || path.equals("/actuator/health");
    }

    private GatewayUser parseUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"请先登录\"}");
            return null;
        }
        try {
            return gatewayJwtService.parse(authorization.substring(7));
        } catch (IllegalArgumentException exception) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"code\":\"INVALID_TOKEN\",\"message\":\"登录令牌无效或已过期\"}");
            return null;
        }
    }

    private boolean allowed(String path, GatewayUser user) {
        if (path.startsWith("/api/hr/")) {
            return "HR".equals(user.role());
        }
        if (path.startsWith("/api/employee/")) {
            return "EMPLOYEE".equals(user.role()) || "HR".equals(user.role());
        }
        if (path.startsWith("/api/ai/")) {
            return true;
        }
        return true;
    }
}
