package com.smartats.module.auth.service;

import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.auth.dto.request.LoginRequest;
import com.smartats.module.auth.dto.request.RegisterRequest;
import com.smartats.module.auth.dto.response.LoginResponse;
import com.smartats.module.auth.entity.User;
import com.smartats.module.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JdbcTemplate jdbcTemplate;

    /**
     * 用户注册
     */
    public void register(RegisterRequest request) {
        // 模拟实现（暂时返回成功）
        System.out.println("用户注册：" + request.getUsername());
    }

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest request) {
        // 模拟实现（暂时返回固定用户）
        String accessToken = jwtUtil.generateToken(1L, "testuser", "HR");
        String refreshToken = jwtUtil.generateRefreshToken(1L, "testuser");

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                1L,
                "testuser",
                "test@example.com",
                "HR",
                100,
                0
        );

        System.out.println("用户登录：" + request.getUsername());
        return new LoginResponse(accessToken, refreshToken, 7200L, userInfo);
    }
}
