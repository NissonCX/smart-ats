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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // 认证接口：允许匿名访问
                        .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/send-verification-code").permitAll()

                        // Webhook 接口：允许外部调用
                        .requestMatchers(HttpMethod.POST, "/webhooks/**").permitAll()

                        // 职位接口：允许匿名访问
                        .requestMatchers(HttpMethod.GET, "/jobs/**").permitAll()

                        // 简历上传接口：需要认证（需要 JWT Token）
                        // 注意：这里不需要显式配置，因为 anyRequest().authenticated() 已经覆盖
                        // 但为了清晰，我们可以显式声明

                        // 其他所有接口：需要认证
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);


        return http.build();
    }

    /**
     * CORS 配置
     * 生产环境应该配置具体的域名，而不是使用 "*"
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            CorsConfiguration config = new CorsConfiguration();

            // TODO: 生产环境应该配置具体的允许域名
            // config.setAllowedOrigins(Arrays.asList("https://your-frontend-domain.com"));

            // 开发环境：允许所有来源（仅用于开发）
            config.setAllowedOriginPatterns(List.of("*"));

            config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
            config.setAllowedHeaders(Arrays.asList("*"));
            config.setExposedHeaders(Arrays.asList("Authorization"));
            config.setAllowCredentials(true);
            config.setMaxAge(3600L);

            return config;
        };
    }

}
