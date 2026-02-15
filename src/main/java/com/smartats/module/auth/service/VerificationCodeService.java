package com.smartats.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.infrastructure.email.EmailService;
import com.smartats.module.auth.entity.User;
import com.smartats.module.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务
 *
 * 功能：
 * 1. 生成 6 位随机验证码
 * 2. 发送验证码到邮箱
 * 3. 验证验证码是否正确
 * 4. 防刷限制（60 秒内只能发送一次）
 * 5. 检查邮箱是否已注册
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private final EmailService emailService;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;

    // Redis Key 前缀
    private static final String CODE_KEY_PREFIX = "verification_code:";
    private static final String LIMIT_KEY_PREFIX = "verification_code_limit:";

    // 验证码长度
    private static final int CODE_LENGTH = 6;

    // 过期时间
    private static final long CODE_EXPIRE_SECONDS = 300;        // 5 分钟
    private static final long LIMIT_EXPIRE_SECONDS = 60;         // 60 秒

    /**
     * 发送验证码
     *
     * @param email 邮箱地址
     */
    public void sendVerificationCode(String email) {
        log.info("发送验证码请求：email={}", email);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 1 步：检查邮箱是否已注册
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        Long emailCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (emailCount > 0) {
            log.warn("发送验证码失败：邮箱已被注册 - email={}", email);
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 2 步：检查防刷限制
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        String limitKey = LIMIT_KEY_PREFIX + email;
        Boolean isLimited = redisTemplate.hasKey(limitKey);

        if (Boolean.TRUE.equals(isLimited)) {
            log.warn("验证码发送频繁：email={}", email);
            throw new BusinessException(ResultCode.VERIFICATION_CODE_SEND_TOO_FREQUENT);
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 3 步：生成 6 位随机验证码
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        String code = generateCode();
        log.info("生成验证码：email={}, code={}", email, code);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 4 步：存储验证码到 Redis（5 分钟过期）
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        String codeKey = CODE_KEY_PREFIX + email;
        redisTemplate.opsForValue().set(codeKey, code, CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 5 步：存储防刷标记（60 秒过期）
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        redisTemplate.opsForValue().set(limitKey, "1", LIMIT_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 6 步：发送邮件
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        boolean success = emailService.sendVerificationCode(email, code);

        if (!success) {
            log.error("验证码邮件发送失败：email={}", email);
            throw new BusinessException(ResultCode.VERIFICATION_CODE_SEND_FAILED);
        }

        log.info("验证码发送成功：email={}", email);
    }

    /**
     * 验证验证码
     *
     * @param email 邮箱地址
     * @param code 用户输入的验证码
     * @return 是否验证成功
     */
    public boolean verifyCode(String email, String code) {
        log.info("验证验证码：email={}, code={}", email, code);

        String codeKey = CODE_KEY_PREFIX + email;
        String storedCode = redisTemplate.opsForValue().get(codeKey);

        if (storedCode == null) {
            log.warn("验证码不存在或已过期：email={}", email);
            return false;
        }

        boolean isValid = storedCode.equals(code);

        if (isValid) {
            log.info("验证码验证成功：email={}", email);

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // 验证成功后删除验证码（一次性使用）
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            redisTemplate.delete(codeKey);
        } else {
            log.warn("验证码验证失败：email={}, input={}, stored={}", email, code, storedCode);
        }

        return isValid;
    }

    /**
     * 生成 6 位随机数字验证码
     *
     * @return 6 位数字字符串
     */
    private String generateCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();

        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));  // 0-9 随机数字
        }

        return code.toString();
    }
}
