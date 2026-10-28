package com.exam.exam_system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @MapperScan 告诉 Spring：去 com.exam.exam_system.mapper 包下扫描所有 @Mapper 接口并注册成 Bean
 * 这样 Service 才能 @Autowired 注入 SysUserMapper
 *
 * <p>@EnableAsync 开启异步方法支持，用于操作日志异步入库
 * （审计日志不是业务关键路径，异步执行避免阻塞业务接口）
 */
@SpringBootApplication
@MapperScan("com.exam.exam_system.mapper")
@EnableAsync
public class ExamSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamSystemApplication.class, args);
    }

}
