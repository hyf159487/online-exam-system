package com.exam.exam_system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求体：JSON 入参对应这个对象
 * @NotBlank 由 spring-boot-starter-validation 提供，触发自动校验
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
