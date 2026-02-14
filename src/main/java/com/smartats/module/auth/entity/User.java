package com.smartats.module.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类
 *
 * @TableName: 对应数据库表名 users
 * @Data: Lombok 自动生成 getter/setter/toString/equals
 */
@Data
@TableName("users")
public class User {

    /**
     * 主键 ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名（唯一）
     */
    private String username;

    /**
     * 密码（BCrypt 加密存储）
     */
    private String password;

    /**
     * 邮箱（唯一）
     */
    private String email;

    /**
     * 角色：ADMIN/HR/INTERVIEWER
     */
    private String role;

    /**
     * 每日 AI 调用配额
     */
    private Integer dailyAiQuota;

    /**
     * 账号状态：0-禁用 1-启用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}