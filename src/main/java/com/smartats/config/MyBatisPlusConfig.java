package com.smartats.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类
 * <p>
 * 将 @MapperScan 从启动类移到此处，保证 @WebMvcTest 切片测试不会加载 Mapper Bean。
 * @WebMvcTest 的 WebMvcTypeExcludeFilter 会排除非 Web 相关的 @Configuration 类。
 */
@Configuration
@MapperScan("com.smartats.module.*.mapper")
public class MyBatisPlusConfig {
}
