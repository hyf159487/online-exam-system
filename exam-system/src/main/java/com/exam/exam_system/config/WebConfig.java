package com.exam.exam_system.config;

import com.exam.exam_system.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web MVC 配置：注册登录 + 角色权限拦截器
 *
 * <p>规则：拦截 /api/** 下所有接口，
 * 但下面这些"白名单"直接放行（不需要 token）：
 * <ul>
 *   <li>/api/auth/login  —— 登录本身（还没 token 怎么登录）</li>
 *   <li>/api/hello       —— 测试接口</li>
 * </ul>
 *
 * <p>拦截器内部在验证 token 后，还会按「路径前缀 + HTTP 方法」
 * 做角色权限校验（学生 / 教师 / 管理员），防止越权访问。
 * 角色约定：0=学生 1=教师 2=管理员。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    /** 上传根目录（与 application.properties 的 upload.dir 一致），静态资源映射到它 */
    @Value("${upload.dir:./uploads/}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")          // 拦截范围：所有 /api 开头的接口
                .excludePathPatterns(                // 白名单：不需要登录就能访问
                        "/api/auth/login",
                        "/api/hello"
                );
    }

    /**
     * 静态资源映射：把上传目录映射成 /uploads/**，方便浏览器直接预览（如 PDF）。
     * 学习资料/导入文件都存到 uploadDir 下，open in browser 直接可访问。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolute = Paths.get(uploadDir).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + (absolute.endsWith("/") ? absolute : absolute + "/"));
    }

    /**
     * 跨域配置：让部署在 Vercel / 其他域名的前端能访问本后端 API
     * 本地开发走 Vite 代理（同源），不需要；部署上线后浏览器跨域访问需要。
     * allowedOriginPatterns("*") 允许任意来源，正式环境建议改为前端具体域名，如：
     * .allowedOriginPatterns("https://你的项目.vercel.app")
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
        // 上传的静态文件（/uploads/**）也要允许跨域，否则线上部署后 PDF 预览会跨域失败
        registry.addMapping("/uploads/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
