package com.smartats.module.auth.filter;

import com.smartats.common.constants.RedisKeyConstants;
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

            String redisKey = RedisKeyConstants.JWT_TOKEN_KEY_PREFIX + userId;
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
                    new SimpleGrantedAuthority(RedisKeyConstants.ROLE_PREFIX + role)
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