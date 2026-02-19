package com.smartats.common.constants;

/**
 * Redis Key 常量定义
 * <p>
 * 统一管理所有 Redis Key 前缀，避免硬编码和拼写错误
 * <p>
 * 命名规范：
 * - 全大写，下划线分隔
 * - 以 _KEY_PREFIX 或 _KEY 结尾
 * - 使用冒号分隔层级（如：jwt:token:{userId}）
 */
public class RedisKeyConstants {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // JWT Token 相关
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * JWT AccessToken Key 前缀
     * <p>
     * 完整格式：jwt:token:{userId}
     * <p>
     * Value：accessToken 字符串
     * <p>
     * TTL：2小时（与 accessToken 过期时间一致）
     */
    public static final String JWT_TOKEN_KEY_PREFIX = "jwt:token:";

    /**
     * JWT RefreshToken Key 前缀
     * <p>
     * 完整格式：jwt:refresh:{userId}
     * <p>
     * Value：refreshToken 字符串
     * <p>
     * TTL：7天（与 refreshToken 过期时间一致）
     */
    public static final String JWT_REFRESH_TOKEN_KEY_PREFIX = "jwt:refresh:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 验证码相关
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 邮箱验证码 Key 前缀
     * <p>
     * 完整格式：verify:code:{email}
     * <p>
     * Value：验证码（6位数字）
     * <p>
     * TTL：5分钟
     */
    public static final String VERIFICATION_CODE_KEY_PREFIX = "verify:code:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // AI 配额相关
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * AI 调用次数限制 Key 前缀
     * <p>
     * 完整格式：rate:ai:{userId}:{date}
     * <p>
     * Value：今日已调用次数
     * <p>
     * TTL：24小时（自然日过期）
     */
    public static final String AI_QUOTA_KEY_PREFIX = "rate:ai:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 任务状态相关
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 简历解析任务状态 Key 前缀
     * <p>
     * 完整格式：task:resume:{taskId}
     * <p>
     * Value：JSON 格式的任务状态（包含 status、progress、message 等）
     * <p>
     * TTL：24小时
     */
    public static final String RESUME_TASK_KEY_PREFIX = "task:resume:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 角色前缀
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Spring Security 角色前缀
     * <p>
     * 格式：ROLE_{role}
     * <p>
     * 示例：ROLE_ADMIN、ROLE_HR、ROLE_INTERVIEWER
     */
    public static final String ROLE_PREFIX = "ROLE_";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 构造函数
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 私有构造函数，防止实例化
     *
     * @throws UnsupportedOperationException 如果尝试实例化
     */
    private RedisKeyConstants() {
        throw new UnsupportedOperationException("常量类不允许实例化");
    }
}
