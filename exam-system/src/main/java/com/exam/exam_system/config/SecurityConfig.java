package com.exam.exam_system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 通用配置：注册 Spring 容器里的 Bean
 * 这里只放 BCrypt 加密器（密码哈希 / 校验）
 *
 * <p>Spring Security 的 crypto 模块只依赖一个 jar，不引入整个 spring-security 也可用
 */
@Configuration
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
