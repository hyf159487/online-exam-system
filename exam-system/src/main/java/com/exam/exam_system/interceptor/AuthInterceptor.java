package com.exam.exam_system.interceptor;

import com.exam.exam_system.mapper.SysUserMapper;
import com.exam.exam_system.service.RedisCacheService;
import com.exam.exam_system.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：所有需要登录的接口，必须带合法 token 才放行
 *
 * <p>前端请求时要把 token 放在请求头里：
 * <pre>
 * Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.xxx.yyy
 * </pre>
 *
 * <p>校验通过后，会把解析出的用户信息放进 request 属性，
 * Controller 里随时可以取：
 * <pre>
 * Long userId = (Long) request.getAttribute("userId");
 * Integer role = (Integer) request.getAttribute("role");
 * </pre>
 *
 * <p>在登录校验之后还会做一次 <b>角色权限校验</b>，防止
 * 低权限用户（如学生）拿着自己的 token 直接调教师/管理接口。
 * 角色约定（与 sys_user.role 一致）：
 * <ul>
 *     <li>0 = 学生</li>
 *     <li>1 = 教师</li>
 *     <li>2 = 管理员</li>
 * </ul>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired(required = false)
    private RedisCacheService redisCacheService;

    /** 登录 token 缓存 key 前缀（与 AuthController 保持一致） */
    private static final String TOKEN_KEY_PREFIX = "auth:token:";

    /** 登出黑名单 key 前缀 */
    private static final String TOKEN_BLACKLIST_PREFIX = "auth:blacklist:";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        // 0. 放行 CORS 预检请求（跨域访问时浏览器会先发 OPTIONS，不带 Authorization 头）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 1. 从请求头拿 Authorization
        String auth = request.getHeader("Authorization");

        // 2. 头不存在 / 格式不对（必须是 "Bearer xxx"）
        if (auth == null || !auth.startsWith("Bearer ")) {
            writeUnauthorized(response, "未登录或 token 缺失");
            return false;   // false = 拦下，不放行
        }

        // 3. 取出 Bearer 后面的 token 本体
        String token = auth.substring(7);   // "Bearer ".length() == 7

        // 4. 解析验签（过期/篡改/格式错都会抛异常）
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            writeUnauthorized(response, "token 已过期，请重新登录");
            return false;
        } catch (Exception e) {
            writeUnauthorized(response, "token 无效");
            return false;
        }

        // 4.5 Redis 登录态校验（登出黑名单 / 强制下线 / 单点登录）。
        // Redis 未配置或不可用时自动跳过，不影响原有 JWT 校验逻辑
        if (redisCacheService != null) {
            // 该 token 已登出 → 立即失效
            if (redisCacheService.hasKey(TOKEN_BLACKLIST_PREFIX + token)) {
                writeUnauthorized(response, "登录已失效，请重新登录");
                return false;
            }
            // 该用户登录 token 缓存与当前 token 不一致 → 已被强制下线 / 在其他设备登录
            Long userId = claims.get("userId", Long.class);
            String cachedToken = redisCacheService.get(TOKEN_KEY_PREFIX + userId);
            if (cachedToken != null && !cachedToken.equals(token)) {
                writeUnauthorized(response, "账号已在其他设备登录，请重新登录");
                return false;
            }
        }

        // 5. 验证通过：把用户信息挂到本次请求上，后续 Controller 可直接用
        Long userId = claims.get("userId", Long.class);
        String username = claims.getSubject();
        Integer role = claims.get("role", Integer.class);
        request.setAttribute("userId", userId);
        request.setAttribute("username", username);      // 小写（兼容旧代码）
        request.setAttribute("userName", username);      // 驼峰（兼容 Controller 里常见取法）
        request.setAttribute("role", role);
        // 额外查一次 sys_user，把真实姓名注入（用于 practice_record.student_name 等）
        try {
            com.exam.exam_system.entity.SysUser u = sysUserMapper.selectById(userId);
            if (u != null && u.getRealName() != null) {
                request.setAttribute("realName", u.getRealName());
            } else {
                request.setAttribute("realName", username);
            }
        } catch (Exception e) {
            request.setAttribute("realName", username);
        }

        // 6. 角色权限校验：按 路径前缀 + HTTP 方法 决定所需角色
        String forbiddenMsg = checkRole(request.getRequestURI(),
                request.getMethod(), claims.get("role", Integer.class));
        if (forbiddenMsg != null) {
            writeForbidden(response, forbiddenMsg);
            return false;
        }

        return true;    // true = 放行
    }

    /**
     * 角色权限校验。
     *
     * <p>规则：
     * <ul>
     *     <li>/api/student/**                        —— 仅学生(0)</li>
     *     <li>/api/teacher/**、/api/sys-user/**、
     *         /api/question/**、/api/exam-paper/**、
     *         /api/grade/**                          —— 教师/管理员(1,2)</li>
     *     <li>/api/exam 的写操作(POST/PUT/DELETE) 与
     *         /api/exam/count                        —— 教师/管理员(1,2)</li>
     *     <li>/api/operation-log/**                  —— 仅管理员(2)：审计日志</li>
     *     <li>/api/audit/waitList、/api/audit/detail/**、
     *         /api/audit/logs/**                     —— 教师/管理员(1,2)：教师查看自己题目的审核进度</li>
     *     <li>/api/audit 的其余接口（pass/reject/historyList/export）—— 仅管理员(2)</li>
     *     <li>/api/study-resource/**                 —— 教师/管理员(1,2)：学习资料管理</li>
     *     <li>/api/knowledge-point/** 的 GET         —— 教师/管理员(1,2)：只读（题目录入加载知识点）
     *         其他方法                                —— 仅管理员(2)：知识点统一由管理员维护</li>
     *     <li>其余（/api/course、/api/exam 的 GET、
     *         /api/auth/me 等）                      —— 已登录即可</li>
     * </ul>
     *
     * @param path   请求路径，如 /api/exam/count
     * @param method HTTP 方法，如 GET / POST
     * @param role   当前用户角色（0学生 1教师 2管理员）
     * @return 通过返回 null；不通过返回给前端展示的错误提示
     */
    private String checkRole(String path, String method, Integer role) {
        if (role == null) {
            return "登录状态异常，请重新登录";
        }
        // 学生端专属接口：仅学生可访问
        if (path.startsWith("/api/student")) {
            return role == 0 ? null : "该接口仅学生可访问";
        }
        // 操作日志接口：仅管理员可访问（审计数据不暴露给教师/学生）
        if (path.startsWith("/api/operation-log")) {
            return role == 2 ? null : "该接口仅管理员可访问";
        }
        // RBAC 角色管理：仅管理员可访问（角色增删改、分配菜单权限）
        if (path.startsWith("/api/role")) {
            return role == 2 ? null : "该接口仅管理员可访问";
        }
        // 字典管理接口：
        //   GET  /api/dict/items/code/{typeCode}  所有登录用户可访问（业务下拉框动态取选项）
        //   GET  /api/dict/**                   所有登录用户可访问（只读）
        //   POST/PUT/DELETE /api/dict/**        仅管理员
        if (path.startsWith("/api/dict")) {
            boolean isRead = "GET".equalsIgnoreCase(method);
            return isRead ? null : (role == 2 ? null : "该接口仅管理员可访问");
        }
        // 系统参数配置接口：
        //   GET  /api/config/value/{key}  所有登录用户可访问（业务代码读配置）
        //   其他                           仅管理员
        if (path.startsWith("/api/config")) {
            boolean isValueRead = path.matches("/api/config/value/.+") && "GET".equalsIgnoreCase(method);
            if (isValueRead) return null;
            return role == 2 ? null : "该接口仅管理员可访问";
        }
        // RBAC 菜单接口：
        //   GET /api/menu/my        所有登录用户可访问（重新拉自己的菜单）
        //   GET /api/menu/tree      仅管理员（分配权限弹窗用）
        //   POST/PUT/DELETE /api/menu  仅管理员（增删改菜单）
        if (path.startsWith("/api/menu")) {
            boolean isMyMenus = path.equals("/api/menu/my");
            if (isMyMenus) {
                return null;       // 所有登录用户都能拉自己的菜单
            }
            return role == 2 ? null : "该接口仅管理员可访问";
        }
        // 试题审核接口：
        //   只读查询（待审核/已驳回列表、题目详情、审核流水）—— 教师/管理员(1,2)
        //     教师端用于查看自己提交题目的审核进度、查看被驳回原因后修改重提
        //   审核写操作（pass / reject / batchPass / batchReject）、历史记录、导出 —— 仅管理员(2)
        if (path.startsWith("/api/audit")) {
            boolean teacherReadable = path.equals("/api/audit/waitList")
                    || path.startsWith("/api/audit/detail/")
                    || path.startsWith("/api/audit/logs/");
            if (teacherReadable) {
                return role >= 1 ? null : "该接口需要教师或管理员权限";
            }
            return role == 2 ? null : "该接口仅管理员可访问";
        }
        // 学习资料管理接口：教师/管理员可访问（学生仅能用 /api/student/** 的只读接口下载，
        // 防止学生直接调管理接口上传/删除资料）
        if (path.startsWith("/api/study-resource")) {
            return role >= 1 ? null : "该接口需要教师或管理员权限";
        }
        // 知识点接口：
        //   GET  /api/knowledge-point/**  教师/管理员可读（题目录入下拉需加载知识点）
        //   POST/PUT/DELETE               仅管理员（知识点统一由管理员维护）
        if (path.startsWith("/api/knowledge-point")) {
            boolean isRead = "GET".equalsIgnoreCase(method);
            if (isRead) {
                return role >= 1 ? null : "该接口需要教师或管理员权限";
            }
            return role == 2 ? null : "该接口仅管理员可访问";
        }
        // 课程加入申请审核接口：仅教师（管理员不做课程申请审核）
        if (path.startsWith("/api/teacher/course-apply")) {
            return role == 1 ? null : "该接口仅教师可访问";
        }
        // 教师/管理端接口：教师或管理员可访问
        boolean teacherArea = path.startsWith("/api/teacher")
                || path.startsWith("/api/sys-user")
                || path.startsWith("/api/question")
                || path.startsWith("/api/exam-paper")
                || path.startsWith("/api/grade");
        if (teacherArea) {
            return role >= 1 ? null : "该接口需要教师或管理员权限";
        }
        // 考试管理：写操作与统计需要教师/管理员
        if (path.startsWith("/api/exam")) {
            boolean isWrite = !"GET".equalsIgnoreCase(method);
            boolean isCount = path.endsWith("/count");
            if (isWrite || isCount) {
                return role >= 1 ? null : "该接口需要教师或管理员权限";
            }
        }
        return null;
    }

    /** 统一给前端返回 401 JSON（和 Result 格式保持一致） */
    private void writeUnauthorized(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);           // HTTP 状态码 401
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":401,\"msg\":\"" + msg + "\",\"data\":null}");
    }

    /** 统一给前端返回 403 JSON（登录成功但权限不足） */
    private void writeForbidden(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);              // HTTP 状态码 403
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":403,\"msg\":\"" + msg + "\",\"data\":null}");
    }
}
