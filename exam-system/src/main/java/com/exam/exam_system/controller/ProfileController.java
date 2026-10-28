package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 通用「个人中心」接口（管理员 / 教师 / 学生 三种角色都能用）。
 *
 * <p>路径前缀 /api/profile。
 * 权限：AuthInterceptor 对非 /api/student/**、非 /api/operation-log/**、非 /api/role/** 的已登录请求一律放行，
 * 因此 /api/profile 天然支持任意已登录用户，且 userId/role 由拦截器统一注入到 request attribute。
 *
 * <p>接口清单：
 * <ul>
 *     <li>GET  /api/profile/get        获取当前登录用户的个人信息（通用字段：username/realName/role/roleName/className/grade/department/phone/email/status）</li>
 *     <li>PUT  /api/profile/update     更新当前账号联系方式（仅手机号、邮箱，其余字段后端丢弃）</li>
 *     <li>PUT  /api/profile/updatePwd  修改当前账号登录密码（原密码 + 新密码 BCrypt 校验）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @Autowired
    private UserService userService;

    /**
     * 获取当前登录用户的个人信息（任意角色通用）。
     * 不需要传参，userId 由 AuthInterceptor 从 JWT 解析后挂到 request attribute。
     */
    @GetMapping("/get")
    public Result<Map<String, Object>> get(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> profile = userService.getProfile(userId);
        return Result.success("查询成功", profile);
    }

    /**
     * 更新当前登录账号的联系方式（仅手机号、邮箱；身份字段 SQL 层就被丢弃无法篡改）。
     * 请求体示例：{ "phone":"13800138000", "email":"test@qq.com" }
     */
    @PutMapping("/update")
    public Result<Void> update(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        // 仅取 phone/email 两个字段，其他字段一律忽略
        Object phone = body == null ? null : body.get("phone");
        Object email = body == null ? null : body.get("email");
        String p = phone == null ? null : String.valueOf(phone);
        String e = email == null ? null : String.valueOf(email);
        userService.updateContact(userId, p, e);
        return Result.success("个人信息修改成功", null);
    }

    /**
     * 上传/更新当前登录账号的头像（任意角色通用）。
     * POST /api/profile/avatar   form-data: file=xxx.png
     *
     * <p>返回可访问路径（形如 /uploads/avatar/202609/xxx.png），
     * 前端把它直接用于头像展示；旧数据 avatar 为空时前端显示姓名首字占位。
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            return Result.success("头像上传成功", userService.updateAvatar(userId, file));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 当前用户自助修改密码（任意角色通用）。
     * 请求体示例：{ "oldPassword":"旧密码明文", "newPassword":"新密码明文" }
     *
     * <p>后端校验顺序：
     * <ol>
     *     <li>从 token 拿当前用户 ID</li>
     *     <li>非空校验 + 新密码 6~16 位字母数字</li>
     *     <li>原密码 BCrypt 比对库中密码哈希</li>
     *     <li>新密码不能与原密码相同</li>
     *     <li>BCrypt 加密后写回 sys_user.password</li>
     * </ol>
     * 成功后建议前端清 token 并跳登录页，强制重新登录。
     */
    @PutMapping("/updatePwd")
    public Result<Void> updatePwd(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Object oldPwd = body == null ? null : body.get("oldPassword");
        Object newPwd = body == null ? null : body.get("newPassword");
        String oldP = oldPwd == null ? null : String.valueOf(oldPwd);
        String newP = newPwd == null ? null : String.valueOf(newPwd);
        userService.changePassword(userId, oldP, newP);
        return Result.success("密码修改成功，请重新登录", null);
    }
}
