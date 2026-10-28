package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 学生端「个人信息」接口
 *
 * <p>路径前缀 /api/student/profile，被 AuthInterceptor 划定为「仅学生(0)可访问」，
 * 教师/管理员即使持有有效 token 也无法调用。
 *
 * <p>接口清单：
 * <ul>
 *     <li>GET /api/student/profile/get     获取当前登录学生个人信息</li>
 *     <li>PUT /api/student/profile/update  更新手机号、邮箱（其余字段后端丢弃）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/student/profile")
public class StudentProfileController {

    @Autowired
    private UserService userService;

    /**
     * 获取当前登录学生的个人信息。
     * 不需要传参，userId 由 AuthInterceptor 从 JWT 解析后挂到 request attribute。
     */
    @GetMapping("/get")
    public Result<Map<String, Object>> get(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> profile = userService.getStudentProfile(userId);
        return Result.success("查询成功", profile);
    }

    /**
     * 更新当前登录学生的联系方式（仅手机号、邮箱）。
     * 请求体示例：{ "phone":"13800138000", "email":"test@qq.com" }
     *
     * <p>后端防护：学号/姓名/班级/年级/院系等身份字段即便被恶意传入也会被直接丢弃，
     * SQL 层只更新 phone、email 两列。
     */
    @PutMapping("/update")
    public Result<Void> update(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        // 仅取 phone/email 两个字段，其他字段一律忽略
        Object phone = body == null ? null : body.get("phone");
        Object email = body == null ? null : body.get("email");
        String p = phone == null ? null : String.valueOf(phone);
        String e = email == null ? null : String.valueOf(email);
        userService.updateStudentContact(userId, p, e);
        return Result.success("个人信息修改成功", null);
    }

    /**
     * 学生自助修改密码。
     * 请求体示例：{ "oldPassword":"旧密码", "newPassword":"新密码" }
     *
     * <p>后端逻辑：
     * <ol>
     *     <li>从 token 拿当前学生 ID</li>
     *     <li>校验原密码是否与库中 BCrypt 哈希匹配</li>
     *     <li>校验新旧密码不能相同</li>
     *     <li>新密码 BCrypt 加密后写入 sys_user.password</li>
     * </ol>
     * <p>建议前端成功后清掉 token 跳登录页，强制重新登录。
     */
    @PutMapping("/updatePwd")
    public Result<Void> updatePwd(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Object oldPwd = body == null ? null : body.get("oldPassword");
        Object newPwd = body == null ? null : body.get("newPassword");
        String oldP = oldPwd == null ? null : String.valueOf(oldPwd);
        String newP = newPwd == null ? null : String.valueOf(newPwd);
        userService.changeStudentPassword(userId, oldP, newP);
        return Result.success("密码修改成功，请重新登录", null);
    }
}
