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

    /**
     * 简历解析分布式锁 Key 前缀
     * <p>
     * 完整格式：lock:resume:{fileHash}
     * <p>
     * Value：锁标识
     * <p>
     * TTL：10分钟
     */
    public static final String RESUME_LOCK_KEY_PREFIX = "lock:resume:";

    /**
     * 简历解析幂等性检查 Key 前缀
     * <p>
     * 完整格式：idempotent:resume:{resumeId}
     * <p>
     * Value：已处理标记
     * <p>
     * TTL：1小时
     */
    public static final String RESUME_IDEMPOTENT_KEY_PREFIX = "idempotent:resume:";

    /**
     * 简历文件去重 Key 前缀
     * <p>
     * 完整格式：dedup:resume:{fileHash}
     * <p>
     * Value：已存在标记
     * <p>
     * TTL：7天
     */
    public static final String RESUME_DEDUP_KEY_PREFIX = "dedup:resume:";

    /**
     * 批量上传频率限制 Key 前缀
     * <p>
     * 完整格式：rate:upload:{userId}
     * <p>
     * Value：当前分钟内批量上传次数
     * <p>
     * TTL：60秒
     */
    public static final String UPLOAD_RATE_LIMIT_KEY_PREFIX = "rate:upload:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 职位缓存相关
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 职位详情缓存 Key 前缀
     * <p>
     * 完整格式：cache:job:{jobId}
     * <p>
     * Value：JSON 格式的职位详情对象
     * <p>
     * TTL：30分钟
     */
    public static final String CACHE_JOB_KEY_PREFIX = "cache:job:";

    /**
     * 热门职位排行 Key
     * <p>
     * 完整格式：cache:job:hot
     * <p>
     * Type：ZSet（有序集合）
     * <p>
     * Score：浏览次数
     * <p>
     * TTL：10分钟
     */
    public static final String CACHE_JOB_HOT_KEY = "cache:job:hot";

    /**
     * 职位浏览量计数器 Key 前缀
     * <p>
     * 完整格式：counter:job:view:{jobId}
     * <p>
     * Value：浏览量计数（纯数字）
     * <p>
     * TTL：永不过期（由定时任务同步到数据库后手动删除）
     */
    public static final String COUNTER_JOB_VIEW_PREFIX = "counter:job:view:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 候选人缓存相关
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 候选人详情缓存 Key 前缀
     * <p>
     * 完整格式：cache:candidate:{candidateId}
     * <p>
     * Value：JSON 格式的候选人详情对象
     * <p>
     * TTL：30分钟
     */
    public static final String CACHE_CANDIDATE_KEY_PREFIX = "cache:candidate:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 职位申请缓存相关
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 职位申请详情缓存 Key 前缀
     * 完整格式：cache:application:{applicationId}
     * Value：JSON 格式的申请详情
     * TTL：30分钟
     */
    public static final String CACHE_APPLICATION_KEY_PREFIX = "cache:application:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 面试记录缓存相关
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 面试记录详情缓存 Key 前缀
     * 完整格式：cache:interview:{interviewId}
     * Value：JSON 格式的面试记录详情
     * TTL：30分钟
     */
    public static final String CACHE_INTERVIEW_KEY_PREFIX = "cache:interview:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 招聘分析缓存相关
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 招聘分析总览缓存 Key 前缀
     * <p>
     * 完整格式：cache:analytics:overview:{jobId}:{startDate}:{endDate}
     * <p>
     * Value：JSON 格式的 RecruitmentOverviewDTO
     * <p>
     * TTL：5分钟
     */
    public static final String CACHE_ANALYTICS_KEY_PREFIX = "cache:analytics:overview:";

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
