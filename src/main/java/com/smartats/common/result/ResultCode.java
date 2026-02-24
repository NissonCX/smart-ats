package com.smartats.common.result;

import lombok.Getter;

/**
 * 统一错误码定义
 *
 * @Getter 注解：Lombok 自动生成 get 方法
 */
@Getter
public enum ResultCode {

    // ========== 成功 ==========
    SUCCESS(200, "操作成功"),

    // ========== 通用错误 4xxxx ==========
    BAD_REQUEST(40001, "参数校验失败"), UNAUTHORIZED(40101, "未登录，请先登录"), FORBIDDEN(40301, "无权限访问"), NOT_FOUND(40401, "资源不存在"),

    // ========== 业务错误 5xxxx ==========
    INTERNAL_ERROR(50001, "系统内部错误"), AI_SERVICE_ERROR(50002, "AI服务不可用"), FILE_UPLOAD_ERROR(50003, "文件上传失败"),

    // ========== 认证模块错误 10xxx ==========
    // 用户相关
    USER_NOT_FOUND(10001, "用户不存在"), USER_ALREADY_EXISTS(10002, "用户已存在"), USERNAME_ALREADY_EXISTS(10003, "用户名已存在"), EMAIL_ALREADY_EXISTS(10004, "邮箱已被注册"), PASSWORD_ERROR(10005, "密码错误"), INVALID_CREDENTIALS(10006, "用户名或密码错误"), USER_DISABLED(10007, "账号已禁用"), ACCOUNT_DISABLED(10008, "账号已被禁用，请联系管理员"), TOKEN_INVALID(10009, "Token 无效或已过期"), TOKEN_REFRESH_FAILED(10010, "Token 刷新失败"),

    // ========== 简历模块错误 20xxx ==========
    RESUME_NOT_FOUND(20001, "简历不存在"), RESUME_ALREADY_PARSED(20002, "简历已解析，请勿重复提交"), FILE_TYPE_NOT_SUPPORTED(20003, "不支持的文件类型"), FILE_SIZE_EXCEEDED(20004, "文件大小超限"), RESUME_DUPLICATE(20005, "简历文件已存在"),
    BATCH_UPLOAD_LIMIT_EXCEEDED(20006, "批量上传文件数超限（最多20个）"),
    UPLOAD_RATE_LIMITED(20007, "上传过于频繁，请稍后再试"),

    // ========== AI 相关错误 30xxx ==========
    AI_QUOTA_EXCEEDED(30001, "AI调用次数超限"),
    AI_PARSE_FAILED(30002, "简历解析失败"),

    // ========== 验证码相关错误 11xxx ==========
    VERIFICATION_CODE_SEND_TOO_FREQUENT(11001, "验证码发送过于频繁，请稍后再试"),
    VERIFICATION_CODE_SEND_FAILED(11002, "验证码发送失败，请稍后重试"),
    VERIFICATION_CODE_INVALID(11003, "验证码错误或已过期"),
    VERIFICATION_CODE_REQUIRED(11004, "请先获取验证码"),

    // ========== 职位申请模块错误 42xxx ==========
    APPLICATION_NOT_FOUND(42001, "申请记录不存在"),
    APPLICATION_DUPLICATE(42002, "该候选人已申请此职位"),
    APPLICATION_STATUS_INVALID(42003, "申请状态流转不合法"),
    APPLICATION_JOB_NOT_PUBLISHED(42004, "职位未发布，无法申请"),

    // ========== 面试模块错误 43xxx ==========
    INTERVIEW_NOT_FOUND(43001, "面试记录不存在"),
    INTERVIEW_ALREADY_COMPLETED(43002, "面试已完成，无法修改"),
    INTERVIEW_ALREADY_CANCELLED(43003, "面试已取消"),
    INTERVIEW_TIME_CONFLICT(43004, "面试时间冲突");
    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误消息
     */
    private final String message;

    /**
     * 构造函数
     */
    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
