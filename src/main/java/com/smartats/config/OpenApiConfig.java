package com.smartats.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 文档配置
 * <p>
 * 访问地址：
 * <ul>
 *   <li>Swagger UI: http://localhost:8080/api/v1/swagger-ui.html</li>
 *   <li>OpenAPI JSON: http://localhost:8080/api/v1/v3/api-docs</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer Token";

    @Bean
    public OpenAPI smartAtsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartATS 智能招聘管理系统 API")
                        .description("""
                                SmartATS 是一个面向 HR 的智能招聘管理系统，提供简历上传与 AI 解析、
                                职位管理、候选人管理、申请流程管理、面试安排与反馈、Webhook 事件通知等功能。
                                
                                ## 认证说明
                                - 登录接口返回 `accessToken`，有效期 2 小时
                                - 在请求头中添加 `Authorization: Bearer {token}` 进行认证
                                - Token 过期后使用 `refreshToken` 刷新
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SmartATS Team")
                                .email("support@smartats.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("输入 JWT Token（不含 Bearer 前缀）")));
    }
}
