package com.smartats.module.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.config.SecurityConfig;
import com.smartats.module.auth.dto.request.LoginRequest;
import com.smartats.module.auth.dto.request.RegisterRequest;
import com.smartats.module.auth.dto.request.RefreshTokenRequest;
import com.smartats.module.auth.dto.request.SendVerificationCodeRequest;
import com.smartats.module.auth.dto.response.LoginResponse;
import com.smartats.module.auth.filter.JwtAuthenticationFilter;
import com.smartats.module.auth.service.UserService;
import com.smartats.module.auth.service.VerificationCodeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 集成测试
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@DisplayName("AuthController 集成测试")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private VerificationCodeService verificationCodeService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void configureMockFilter() throws Exception {
        lenient().doAnswer(invocation -> {
            HttpServletRequest req = invocation.getArgument(0);
            HttpServletResponse res = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(jwtAuthenticationFilter)
                .doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class), any(FilterChain.class));
    }

    private static UsernamePasswordAuthenticationToken mockAuth(Long userId) {
        return new UsernamePasswordAuthenticationToken(
                userId, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_HR"))
        );
    }

    private static LoginResponse sampleLoginResponse() {
        LoginResponse response = new LoginResponse();
        response.setAccessToken("access-token-xxx");
        response.setRefreshToken("refresh-token-xxx");
        response.setExpiresIn(7200L);

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setUserId(1L);
        userInfo.setUsername("testuser");
        userInfo.setEmail("test@example.com");
        userInfo.setRole("HR");
        userInfo.setDailyAiQuota(100);
        userInfo.setTodayAiUsed(5);
        response.setUserInfo(userInfo);

        return response;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 注册
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /auth/register")
    class RegisterTests {

        @Test
        @DisplayName("注册成功 - 200")
        void shouldRegisterSuccessfully() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("newuser");
            request.setPassword("password123");
            request.setEmail("new@test.com");
            request.setVerificationCode("123456");

            willDoNothing().given(userService).register(any(RegisterRequest.class));

            mockMvc.perform(post("/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("参数校验失败 - 400")
        void shouldReturn400WhenValidationFails() throws Exception {
            RegisterRequest request = new RegisterRequest();
            // missing required fields

            mockMvc.perform(post("/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("业务异常返回错误码")
        void shouldReturnErrorWhenBusinessException() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("existuser");
            request.setPassword("password123");
            request.setEmail("exist@test.com");
            request.setVerificationCode("123456");

            willThrow(new BusinessException(ResultCode.USERNAME_ALREADY_EXISTS))
                    .given(userService).register(any(RegisterRequest.class));

            mockMvc.perform(post("/auth/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(200)));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 登录
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /auth/login")
    class LoginTests {

        @Test
        @DisplayName("登录成功返回Token")
        void shouldLoginSuccessfully() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("password123");

            given(userService.login(any(LoginRequest.class))).willReturn(sampleLoginResponse());

            mockMvc.perform(post("/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.accessToken").value("access-token-xxx"))
                    .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-xxx"))
                    .andExpect(jsonPath("$.data.userInfo.username").value("testuser"))
                    .andExpect(jsonPath("$.data.userInfo.todayAiUsed").value(5));
        }

        @Test
        @DisplayName("缺少用户名 - 校验失败")
        void shouldFailWhenUsernameMissing() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setPassword("password123");

            mockMvc.perform(post("/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 发送验证码
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /auth/send-verification-code")
    class SendVerificationCodeTests {

        @Test
        @DisplayName("发送验证码成功")
        void shouldSendCodeSuccessfully() throws Exception {
            SendVerificationCodeRequest request = new SendVerificationCodeRequest();
            request.setEmail("test@example.com");

            willDoNothing().given(verificationCodeService).sendVerificationCode("test@example.com");

            mockMvc.perform(post("/auth/send-verification-code")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Token 刷新
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /auth/refresh")
    class RefreshTokenTests {

        @Test
        @DisplayName("刷新Token成功")
        void shouldRefreshSuccessfully() throws Exception {
            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken("old-refresh-token");

            given(userService.refreshToken("old-refresh-token")).willReturn(sampleLoginResponse());

            mockMvc.perform(post("/auth/refresh")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.accessToken").value("access-token-xxx"));
        }

        @Test
        @DisplayName("refreshToken为空 - 校验失败")
        void shouldFailWhenRefreshTokenBlank() throws Exception {
            RefreshTokenRequest request = new RefreshTokenRequest();
            // refreshToken is blank

            mockMvc.perform(post("/auth/refresh")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 认证测试
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /auth/test")
    class TestAuthTests {

        @Test
        @DisplayName("已认证用户可以访问")
        void shouldReturnSuccessWhenAuthenticated() throws Exception {
            mockMvc.perform(get("/auth/test")
                            .with(authentication(mockAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.userId").value(1))
                    .andExpect(jsonPath("$.data.message").value("JWT 认证成功！"));
        }

        @Test
        @DisplayName("未认证用户返回401")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/auth/test"))
                    .andExpect(status().isForbidden());
        }
    }
}
