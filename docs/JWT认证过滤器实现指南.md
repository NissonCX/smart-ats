# JWT 认证过滤器实现指南

> **创建时间**：2026-02-17
> **优先级**：高
> **预计耗时**：30-40 分钟

---

## 📋 任务概述

**当前状态**：
- ✅ 登录接口可以生成 JWT Token
- ✅ JwtUtil 工具类已完成
- ❌ **缺少 JWT 认证过滤器**，Token 无法被验证
- ❌ 所有受保护接口都返回 401

**任务目标**：
实现 `JwtAuthenticationFilter`，让系统能够验证 JWT Token 并保护受保护的 API 接口。同时将 Token 存储在 Redis 中，支持 Token 撤销功能。

---

## 🎯 实现步骤

### 步骤 1：创建 Filter 目录和文件

```bash
# 创建 filter 目录
mkdir -p src/main/java/com/smartats/module/auth/filter

# 创建 JwtAuthenticationFilter.java
touch src/main/java/com/smartats/module/auth/filter/JwtAuthenticationFilter.java
```

---

### 步骤 2：实现 JwtAuthenticationFilter.java

**文件路径**：`src/main/java/com/smartats/module/auth/filter/JwtAuthenticationFilter.java`

```java
package com.smartats.module.auth.filter;

import com.smartats.module.auth.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器
 * <p>
 * 功能：
 * 1. 从请求头提取 JWT Token
 * 2. 验证 Token 有效性（签名、过期时间）
 * 3. 验证 Token 是否存在于 Redis（防止已撤销的 Token）
 * 4. 解析用户信息并存入 SecurityContext
 * <p>
 * Redis 存储策略：
 * - Key: jwt:token:{userId}
 * - Value: accessToken
 * - TTL: 与 Token 过期时间一致（2小时）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JWT_TOKEN_KEY_PREFIX = "jwt:token:";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 1 步：从请求头提取 Token
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        // 检查 Header 是否存在且以 "Bearer " 开头
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("请求未携带有效的 Authorization Header，跳过 JWT 认证");
            filterChain.doFilter(request, response);
            return;
        }

        // 提取 Token（去掉 "Bearer " 前缀）
        String token = authHeader.substring(BEARER_PREFIX.length());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 2 步：解析 Token 获取用户信息
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        try {
            // 解析 Token Claims
            var claims = jwtUtil.parseToken(token);

            // 提取用户信息
            Long userId = claims.get("userId", Long.class);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);

            log.debug("成功解析 JWT Token: userId={}, username={}, role={}", userId, username, role);

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // 第 3 步：验证 Token 是否存在于 Redis（防止已撤销的 Token）
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            String redisKey = JWT_TOKEN_KEY_PREFIX + userId;
            String storedToken = redisTemplate.opsForValue().get(redisKey);

            if (storedToken == null) {
                log.warn("Token 不存在于 Redis 中，可能已被撤销: userId={}", userId);
                filterChain.doFilter(request, response);
                return;
            }

            // 验证 Token 是否匹配（防止 Token 被替换）
            if (!storedToken.equals(token)) {
                log.warn("Token 与 Redis 中存储的不匹配: userId={}", userId);
                filterChain.doFilter(request, response);
                return;
            }

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // 第 4 步：验证 Token 有效性（签名、过期时间）
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            if (!jwtUtil.validateToken(token)) {
                log.warn("Token 验证失败（无效或已过期）: userId={}", userId);
                filterChain.doFilter(request, response);
                return;
            }

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // 第 5 步：检查 SecurityContext 是否已有认证（避免重复认证）
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                log.debug("SecurityContext 已存在认证信息，跳过 JWT 认证");
                filterChain.doFilter(request, response);
                return;
            }

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // 第 6 步：创建 Authentication 对象并存入 SecurityContext
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            // 创建权限列表（此处使用简单实现，后续可扩展为 RBAC）
            var authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + role)
            );

            // 创建认证对象
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userId,           // principal：使用 userId 作为主体
                    null,            // credentials：不需要密码
                    authorities       // authorities：用户权限
                );

            // 设置认证详情（包含 IP、SessionId 等信息）
            authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
            );

            // 存入 SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.info("JWT 认证成功: userId={}, username={}, role={}, ip={}",
                userId, username, role, request.getRemoteAddr());

        } catch (Exception e) {
            log.error("JWT 认证异常: {}", e.getMessage(), e);
            // 异常情况清除 SecurityContext
            SecurityContextHolder.clearContext();
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 第 7 步：继续执行后续过滤器
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        filterChain.doFilter(request, response);
    }
}
```

---

### 步骤 3：更新 SecurityConfig.java

**文件路径**：`src/main/java/com/smartats/config/SecurityConfig.java`

**需要添加的内容**：

```java
// 1. 添加 @RequiredArgsConstructor 注解
@RequiredArgsConstructor
public class SecurityConfig {

    // 2. 注入 JwtAuthenticationFilter
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/send-verification-code").permitAll()
                .anyRequest().authenticated()
            )

            // 3. 添加 JWT 认证过滤器（关键！）
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

**完整参考**：

```java
package com.smartats.config;

import com.smartats.module.auth.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/send-verification-code").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

---

### 步骤 4：更新 UserService.java（登录时存储 Token 到 Redis）

**文件路径**：`src/main/java/com/smartats/module/auth/service/UserService.java`

**在 `login()` 方法中，生成 Token 后添加以下代码**：

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 第 N 步：存储 Token 到 Redis（支持 Token 撤销）
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// 存储 accessToken 到 Redis（Key: jwt:token:{userId}）
String accessTokenKey = "jwt:token:" + user.getId();
redisTemplate.opsForValue().set(
    accessTokenKey,
    accessToken,
    jwtUtil.getExpiration(),
    TimeUnit.SECONDS
);

// 存储 refreshToken 到 Redis（Key: jwt:refresh:{userId}）
String refreshTokenKey = "jwt:refresh:" + user.getId();
redisTemplate.opsForValue().set(
    refreshTokenKey,
    refreshToken,
    jwtUtil.getRefreshExpiration(),
    TimeUnit.SECONDS
);

log.info("Token 已存储到 Redis: userId={}, accessTokenExpire={}s, refreshTokenExpire={}s",
    user.getId(), jwtUtil.getExpiration(), jwtUtil.getRefreshExpiration());
```

---

### 步骤 5：测试验证

#### 5.1 启动应用

```bash
mvn spring-boot:run
```

#### 5.2 登录获取 Token

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "Password123!"
  }'
```

**预期响应**：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 7200,
    "userInfo": { ... }
  },
  "timestamp": 1739452800000
}
```

#### 5.3 验证 Token 存入 Redis

```bash
# 连接 Redis
redis-cli -h 127.0.0.1 -p 6379 -a redis123

# 查看 Token
127.0.0.1:6379> GET jwt:token:1
"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# 查看过期时间
127.0.0.1:6379> TTL jwt:token:1
(integer) 7195  # 剩余秒数
```

#### 5.4 使用 Token 访问受保护接口

```bash
# 创建一个测试接口（如果没有的话）
curl -X GET http://localhost:8080/api/v1/auth/test \
  -H "Authorization: Bearer {你的accessToken}"
```

**预期响应**：
- 成功：返回接口数据（200 OK）
- 失败：返回 401 Unauthorized

---

## 📊 Redis Key 设计

| Key Pattern | 类型 | Value | TTL | 用途 |
|-------------|------|-------|-----|------|
| `jwt:token:{userId}` | String | accessToken | 2小时（7200秒） | 验证请求 Token |
| `jwt:refresh:{userId}` | String | refreshToken | 7天（604800秒） | 刷新 Token |
| `jwt:blacklist:{token}` | String | "revoked" | 直到过期 | Token 黑名单（退出登录时使用） |

---

## 🎯 完成后效果

### 实现前
```bash
# 登录成功
✅ POST /auth/login → 返回 Token

# 访问受保护接口
❌ GET /jobs → 401 Unauthorized（即使带 Token）
```

### 实现后
```bash
# 登录成功
✅ POST /auth/login → 返回 Token 并存入 Redis

# 访问受保护接口（带 Token）
✅ GET /jobs → 200 OK（验证通过）

# 访问受保护接口（不带 Token）
❌ GET /jobs → 401 Unauthorized（正常拒绝）

# 访问受保护接口（带无效 Token）
❌ GET /jobs → 401 Unauthorized（Token 验证失败）
```

---

## 🔍 核心技术点

### 1. OncePerRequestFilter
- 确保每个请求只执行一次过滤
- 适合 JWT 认证场景

### 2. SecurityContext
- 存储当前用户的认证信息
- 后续 Controller 可以通过 `SecurityContextHolder` 获取用户信息

### 3. Bearer Token
- 标准的 HTTP 认证方案
- 格式：`Authorization: Bearer {token}`

### 4. Redis 存储 Token
- 支持 Token 撤销（用户主动退出登录）
- 防止 Token 被盗用后的长期有效
- 实现单点登录（同一用户只能有一个有效 Token）

---

## 📝 后续扩展

完成后可以考虑：

1. **退出登录接口**：删除 Redis 中的 Token
2. **Token 刷新接口**：使用 refreshToken 换取新的 accessToken
3. **Token 黑名单**：将撤销的 Token 加入黑名单
4. **权限控制**：基于角色的访问控制（RBAC）

---

## ⚠️ 注意事项

1. **Filter 顺序**：必须在 `UsernamePasswordAuthenticationFilter` 之前执行
2. **异常处理**：JWT 验证失败时不应抛出异常，应静默跳过
3. **日志记录**：记录认证成功/失败的日志，便于问题排查
4. **性能考虑**：Redis 查询很快，但仍需注意缓存策略

---

**文档创建时间**：2026-02-17
**状态**：待实现
**优先级**：高
