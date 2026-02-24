package com.smartats.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.auth.dto.request.LoginRequest;
import com.smartats.module.auth.dto.request.RegisterRequest;
import com.smartats.module.auth.dto.response.LoginResponse;
import com.smartats.module.auth.entity.User;
import com.smartats.module.auth.mapper.UserMapper;
import com.smartats.module.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final VerificationCodeService verificationCodeService;
    private final StringRedisTemplate redisTemplate;

    /**
     * 用户注册
     */
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        log.info("用户注册请求：username={}, email={}", request.getUsername(), request.getEmail());
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 0 步：验证邮箱验证码（新增）
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        boolean codeValid = verificationCodeService.verifyCode(request.getEmail(), request.getVerificationCode());

        if (!codeValid) {
            log.warn("注册失败：验证码错误 - email={}", request.getEmail());
            throw new BusinessException(ResultCode.VERIFICATION_CODE_INVALID);
        }
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 1 步：校验用户名是否已存在
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        Long usernameCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (usernameCount > 0) {
            log.warn("注册失败：用户名已存在 - {}", request.getUsername());
            throw new BusinessException(ResultCode.USERNAME_ALREADY_EXISTS);
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 2 步：校验邮箱是否已存在
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        Long emailCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail()));
        if (emailCount > 0) {
            log.warn("注册失败：邮箱已存在 - {}", request.getEmail());
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 3 步：密码加密（BCrypt）
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        log.debug("密码已加密");

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 4 步：创建用户对象
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(encodedPassword);
        user.setEmail(request.getEmail());
        user.setRole(request.getRole() != null ? request.getRole() : "HR");
        user.setDailyAiQuota(100);
        user.setStatus(1);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 5 步：保存到数据库
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        int insertResult = userMapper.insert(user);
        if (insertResult <= 0) {
            log.error("注册失败：数据库插入失败 - username={}", request.getUsername());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "注册失败，请稍后重试");
        }

        log.info("注册成功：userId={}, username={}", user.getId(), user.getUsername());
    }

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest request) {
        log.info("用户登录请求：username={}", request.getUsername());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 1 步：根据用户名查询用户
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 2 步：验证用户是否存在
        // ⚠️ 安全要点：统一返回"用户名或密码错误"，防止用户名枚举攻击
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (user == null) {
            log.warn("登录失败：用户不存在 - {}", request.getUsername());
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 3 步：验证密码
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        boolean passwordMatch = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!passwordMatch) {
            log.warn("登录失败：密码错误 - username={}", request.getUsername());
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 4 步：检查账号状态
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (user.getStatus() == 0) {
            log.warn("登录失败：账号已被禁用 - username={}", request.getUsername());
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED, "账号已被禁用，请联系管理员");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 5 步：生成 Token
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        String accessToken = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        log.info("Token 生成成功：userId={}, username={}", user.getId(), user.getUsername());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 6 步：构造响应对象
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        int todayAiUsed = getTodayAiUsed(user.getId());
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId(), user.getUsername(), user.getEmail(),
                user.getRole(), user.getDailyAiQuota(), todayAiUsed
        );

        LoginResponse response = new LoginResponse(accessToken, refreshToken, 7200L,  // expiresIn（秒）
                userInfo);

        log.info("登录成功：userId={}, username={}", user.getId(), user.getUsername());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 6 步：存储 Token 到 Redis（支持 Token 撤销）
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        // 存储 accessToken 到 Redis（Key: jwt:token:{userId}）
        String accessTokenKey = RedisKeyConstants.JWT_TOKEN_KEY_PREFIX + user.getId();
        redisTemplate.opsForValue().set(
                accessTokenKey,
                accessToken,
                jwtUtil.getExpiration(),
                TimeUnit.SECONDS
        );

        // 存储 refreshToken 到 Redis（Key: jwt:refresh:{userId}）
        String refreshTokenKey = RedisKeyConstants.JWT_REFRESH_TOKEN_KEY_PREFIX + user.getId();
        redisTemplate.opsForValue().set(
                refreshTokenKey,
                refreshToken,
                jwtUtil.getRefreshExpiration(),
                TimeUnit.SECONDS
        );

        log.info("Token 已存储到 Redis: userId={}, accessTokenExpire={}s, refreshTokenExpire={}s",
                user.getId(), jwtUtil.getExpiration(), jwtUtil.getRefreshExpiration());
        return response;
    }

    /**
     * 使用 RefreshToken 换取新的 AccessToken
     *
     * @param refreshToken 客户端持有的刷新令牌
     * @return 包含新 AccessToken 的登录响应
     */
    public LoginResponse refreshToken(String refreshToken) {
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 1 步：解析 RefreshToken 获取用户名
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        String username;
        try {
            username = jwtUtil.getUsernameFromToken(refreshToken);
        } catch (Exception e) {
            log.warn("RefreshToken 解析失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "无效的 refreshToken");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 2 步：查询用户信息
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 3 步：比对 Redis 中存储的 RefreshToken
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        String storedRefreshToken = redisTemplate.opsForValue()
                .get(RedisKeyConstants.JWT_REFRESH_TOKEN_KEY_PREFIX + user.getId());
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            log.warn("RefreshToken 不匹配或已失效: userId={}", user.getId());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 已失效，请重新登录");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 4 步：签发新 AccessToken + 轮换 RefreshToken
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        String newAccessToken = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 5 步：更新 Redis
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        redisTemplate.opsForValue().set(
                RedisKeyConstants.JWT_TOKEN_KEY_PREFIX + user.getId(),
                newAccessToken,
                jwtUtil.getExpiration(),
                java.util.concurrent.TimeUnit.SECONDS
        );
        redisTemplate.opsForValue().set(
                RedisKeyConstants.JWT_REFRESH_TOKEN_KEY_PREFIX + user.getId(),
                newRefreshToken,
                jwtUtil.getRefreshExpiration(),
                java.util.concurrent.TimeUnit.SECONDS
        );

        log.info("Token 刷新成功: userId={}, username={}", user.getId(), user.getUsername());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 6 步：构造响应
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        int refreshTodayAiUsed = getTodayAiUsed(user.getId());
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId(), user.getUsername(), user.getEmail(),
                user.getRole(), user.getDailyAiQuota(), refreshTodayAiUsed);

        return new LoginResponse(newAccessToken, newRefreshToken, jwtUtil.getExpiration(), userInfo);
    }

    /**
     * 从 Redis 获取用户今日 AI 调用次数
     * <p>
     * Key 格式：rate:ai:{userId}:{date}（如 rate:ai:1:2026-02-24）
     *
     * @param userId 用户 ID
     * @return 今日已使用次数，无记录时返回 0
     */
    private int getTodayAiUsed(Long userId) {
        try {
            String date = java.time.LocalDate.now().toString();
            String key = RedisKeyConstants.AI_QUOTA_KEY_PREFIX + userId + ":" + date;
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Integer.parseInt(value) : 0;
        } catch (Exception e) {
            log.warn("获取今日 AI 使用次数失败（降级返回0）: userId={}", userId, e);
            return 0;
        }
    }
}
