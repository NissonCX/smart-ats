package com.smartats.module.auth.dto.request;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * 用户登录请求
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
