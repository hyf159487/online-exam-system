package com.exam.exam_system.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA（单页应用）路由转发
 *
 * <p>背景：前端打包后（dist 里的 index.html 等）放在 static 目录由后端直接伺服。
 * 但 Vue Router 用的是 history 模式（地址栏没有 #），用户直接访问或刷新
 * /login、/student/exams 这类地址时，后端没有对应的物理文件，会返回 404。
 *
 * <p>本类的职责：把前端的路由地址统一转发到 /index.html，交给前端路由解析。
 *   - /api/**        接口由各 Controller 精确匹配，不会进这里
 *   - /assets/**     等静态资源（带后缀）由静态资源处理器伺服，不会进这里
 *   - /              根路径由 Spring Boot 欢迎页（static/index.html）处理
 */
@Controller
public class SpaForwardController {

    @GetMapping({
            // 登录页与学生端
            "/login",
            "/student/**",
            // 教师端 / 管理员端各页面（与前端 router 一一对应）
            "/dashboard",
            "/questions/**",
            "/papers/**",
            "/paper-templates/**",
            "/exams/**",
            "/grades/**",
            "/stats/**",
            "/notifications/**",
            "/practice/**",
            "/system/**",
            "/audit/**",
            "/account/**",
            "/classes/**",
            "/courses/**",
            "/profile/**"
    })
    public String spaForward() {
        return "forward:/index.html";
    }
}
