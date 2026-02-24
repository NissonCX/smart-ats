package com.smartats.module.auth.controller;

import com.smartats.common.result.Result;
import com.smartats.module.auth.dto.request.LoginRequest;
import com.smartats.module.auth.dto.request.RefreshTokenRequest;
import com.smartats.module.auth.dto.request.RegisterRequest;
import com.smartats.module.auth.dto.request.SendVerificationCodeRequest;
import com.smartats.module.auth.dto.response.LoginResponse;
import com.smartats.module.auth.service.UserService;
import com.smartats.module.auth.service.VerificationCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@Tag(name = "认证管理", description = "用户注册、登录、Token 刷新、邮箱验证码")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final VerificationCodeService verificationCodeService;
    /**
     * 用户注册
     * POST /api/v1/auth/register
     */
    @Operation(summary = "用户注册", description = "注册新用户，需要邮箱验证码，BCrypt 加密存储密码")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return Result.success();
    }

    /**
     * 用户登录
     * POST /api/v1/auth/login
     */
    @Operation(summary = "用户登录", description = "返回 accessToken(2h) + refreshToken(7d)")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return Result.success(response);
    }

    /**
     * 发送验证码
     * <p>
     * POST /api/v1/auth/send-verification-code
     */
    @Operation(summary = "发送邮箱验证码", description = "60秒发送限流，5分钟有效")
    @PostMapping("/send-verification-code")
    public Result<Void> sendVerificationCode(@Valid @RequestBody SendVerificationCodeRequest request) {
        verificationCodeService.sendVerificationCode(request.getEmail());
        return Result.success();
    }

    /**
     * 刺新 Access Token
     * <p>
     * POST /api/v1/auth/refresh
     * <p>
     * 使用登录时返回的 refreshToken 换取新的 accessToken
     */
    @Operation(summary = "刷新 Token", description = "使用 refreshToken 换取新的 accessToken")
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = userService.refreshToken(request.getRefreshToken());
        return Result.success(response);
    }

    /**
     * 测试 JWT 认证接口
     * <p>
     * GET /api/v1/auth/test
     * <p>
     * 需要携带有效的 JWT Token 才能访问
     * <p>
     * 用于验证 JWT 认证过滤器是否正常工作
     */
    @Operation(summary = "测试 JWT 认证", description = "验证 JWT Token 是否有效")
    @GetMapping("/test")
    public Result<Object> testAuthentication(Authentication authentication) {
        return Result.success(new TestAuthResponse(
                (Long) authentication.getPrincipal(),
                authentication.getAuthorities(),
                "JWT 认证成功！"
        ));
    }

    /**
     * 测试认证响应对象
     */
    private record TestAuthResponse(
            Long userId,
            Object authorities,
            String message
    ) {}
}
