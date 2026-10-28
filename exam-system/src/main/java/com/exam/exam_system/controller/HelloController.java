package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试接口：验证前后端链路是否打通
 * 现在返回的是统一封装的 Result 结构
 */
@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("在线考试系统后端运行正常");
    }
}
