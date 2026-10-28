package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.LoginRequest;
import com.exam.exam_system.dto.LoginResponse;
import com.exam.exam_system.entity.SysMenu;
import com.exam.exam_system.entity.SysUser;
import com.exam.exam_system.service.RedisCacheService;
import com.exam.exam_system.service.SysMenuService;
import com.exam.exam_system.service.UserService;
import com.exam.exam_system.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 认证 Controller：登录、查当前登录用户
 * 学生端、教师端共用，后续可按角色拆细
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private SysMenuService sysMenuService;

    @Autowired
    private JwtUtil jwtUtil;

    /** JWT 有效期（小时），与 redis token 缓存 TTL 保持一致 */
    @Value("${jwt.expire-hours}")
    private long expireHours;

    @Autowired(required = false)
    private RedisCacheService redisCacheService;

    /** Token 缓存 key 前缀 */
    private static final String TOKEN_KEY_PREFIX = "auth:token:";

    /** Token 黑名单 key 前缀（登出后旧 token 立即失效） */
    private static final String TOKEN_BLACKLIST_PREFIX = "auth:blacklist:";

    /**
     * 账号密码登录（教师 / 管理员都走这个）
     *
     * <p>请求示例：
     * <pre>
     * POST /api/auth/login
     * Content-Type: application/json
     * { "username": "admin", "password": "123456" }
     * </pre>
     *
     * <p>成功响应（token 是 JWT，三段式 eyJhbGciOi....xxx.yyy）：
     * <pre>
     * { "code": 200, "msg": "登录成功",
     *   "data": { "token": "eyJhbGci...", "userId": 1, "username": "admin",
     *             "realName": "系统管理员", "role": 2 } }
     * </pre>
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest req) {
        SysUser user = userService.login(req.getUsername(), req.getPassword());
        if (user == null) {
            return Result.error(401, "用户名或密码错误");
        }

        // 正式 JWT：里面装了 userId / username / role，24 小时有效
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());

        // 将 token 缓存到 Redis（TTL 与 JWT 有效期一致），
        // 拦截器据此实现「单点登录/强制下线」；Redis 不可用时自动跳过，不影响登录
        if (redisCacheService != null) {
            redisCacheService.set(TOKEN_KEY_PREFIX + user.getId(), token, expireHours * 3600L);
        }

        // RBAC：根据用户角色查询菜单权限树
        // 优先用 user.roleId（数据库 sys_user.role_id）；
        // 老用户如果 role_id 为空，从 role 字段反推 roleId = role + 1
        Long roleId = user.getRoleId();
        if (roleId == null && user.getRole() != null) {
            roleId = (long) (user.getRole() + 1);
        }
        List<SysMenu> menus = Collections.emptyList();
        if (roleId != null) {
            menus = sysMenuService.listTreeByRoleId(roleId);
        }

        LoginResponse resp = new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getRole(),
                roleId,
                menus);
        return Result.success("登录成功", resp);
    }

    /**
     * 退出登录：把当前 token 加入 Redis 黑名单，使其立即失效
     * （未配置 Redis 时仅清理前端状态，不影响使用）
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String auth = request.getHeader("Authorization");
        if (redisCacheService != null) {
            // 当前 token 拉黑，TTL 与 JWT 有效期一致
            if (auth != null && auth.startsWith("Bearer ")) {
                String token = auth.substring(7);
                redisCacheService.set(TOKEN_BLACKLIST_PREFIX + token, "1", expireHours * 3600L);
            }
            // 删除登录 token 缓存，使该用户所有旧 token 失效
            if (userId != null) {
                redisCacheService.delete(TOKEN_KEY_PREFIX + userId);
            }
        }
        return Result.success("已退出登录", null);
    }

    /**
     * 查当前登录用户信息（受保护接口，用来验证拦截器是否生效）
     *
     * <p>请求时要带请求头：
     * <pre>
     * Authorization: Bearer 登录时拿到的token
     * </pre>
     *
     * <p>不带 token 或 token 无效 → 会被拦截器挡下，返回 401
     */
    @GetMapping("/me")
    public Result<String> me(HttpServletRequest request) {
        // 拦截器验签通过后，用户信息已经挂在 request 上了
        Long userId = (Long) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        Integer role = (Integer) request.getAttribute("role");

        return Result.success("当前登录用户信息",
                "userId=" + userId + ", username=" + username + ", role=" + role);
    }
}
