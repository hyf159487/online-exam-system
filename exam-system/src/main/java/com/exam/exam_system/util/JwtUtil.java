package com.exam.exam_system.util;

import com.exam.exam_system.service.SysConfigService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：生成 token / 解析 token
 *
 * <p>token 里装了什么（Payload/Claims）：
 * <pre>
 * {
 *   "sub": "admin",          // 用户名（JWT 标准字段 subject）
 *   "userId": 1,             // 自定义字段：用户ID
 *   "role": 2,               // 自定义字段：角色（0学生 1教师 2管理员）
 *   "iat": 1787000000,       // 签发时间（JWT 标准字段）
 *   "exp": 1787086400        // 过期时间（JWT 标准字段）
 * }
 * </pre>
 *
 * <p>token 有效期优先从数据库配置读取（configKey=token_expiry_hours），
 * 数据库不可用时 fallback 到 application.properties 的 jwt.expire-hours。
 */
@Component
public class JwtUtil {

    /** 签名密钥（从 application.properties 注入，HS256 至少 32 字节） */
    @Value("${jwt.secret}")
    private String secret;

    /** 默认 token 有效期（小时），数据库不可用时 fallback */
    @Value("${jwt.expire-hours:24}")
    private long defaultExpireHours;

    @Autowired
    private SysConfigService sysConfigService;

    /** 把字符串密钥变成 JWT 库需要的 SecretKey 对象（懒加载，第一次用时创建） */
    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 获取当前 token 有效期（小时），优先从数据库配置读取
     */
    private long getExpireHours() {
        try {
            int hours = sysConfigService.getInt("token_expiry_hours", (int) defaultExpireHours);
            return hours > 0 ? hours : defaultExpireHours;
        } catch (Exception e) {
            return defaultExpireHours;
        }
    }

    /**
     * 登录成功后调用：生成一个 JWT
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param role     角色：0=学生 1=教师 2=管理员
     * @return 形如 eyJhbGciOiJIUzI1NiJ9.xxx.yyy 的字符串
     */
    public String createToken(Long userId, String username, Integer role) {
        long expireHours = getExpireHours();
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireHours * 3600_000L);

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expireAt)
                .signWith(getKey())
                .compact();
    }

    /**
     * 每次请求进来时调用：解析并校验 token
     *
     * @param token 前端带来的 JWT 字符串
     * @return Claims（载荷，可从中取 userId / username / role）
     * @throws io.jsonwebtoken.ExpiredJwtException    token 已过期
     * @throws io.jsonwebtoken.JwtException           token 无效 / 被篡改 / 格式错误
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
