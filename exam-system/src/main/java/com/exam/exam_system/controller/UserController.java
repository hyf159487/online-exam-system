package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.UserQuery;
import com.exam.exam_system.entity.SysUser;
import com.exam.exam_system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户 Controller（教师管理学生、教师互相查看）
 */
@RestController
@RequestMapping("/api/sys-user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 按角色查用户列表（不分页，仪表盘用兼容接口）
     * GET /api/sys-user/list?role=1    ← role=1 查学生
     */
    @GetMapping("/list")
    public Result<List<SysUser>> listByRole(@RequestParam Integer role) {
        return Result.success("查询成功", userService.listByRole(role));
    }

    /**
     * 按角色统计用户数（仪表盘用）
     * GET /api/sys-user/count?role=0   → role=0 学生
     * GET /api/sys-user/count           → 全部
     */
    @GetMapping("/count")
    public Result<Long> countByRole(@RequestParam(required = false) Integer role) {
        return Result.success("查询成功", userService.countByRole(role));
    }

    /**
     * 分页 + 模糊搜索查询用户（学生管理页用）
     * GET /api/sys-user/page?pageNum=1&pageSize=10&role=0&keyword=张
     */
    @GetMapping("/page")
    public Result<PageResult<SysUser>> page(@ModelAttribute UserQuery query) {
        return Result.success("查询成功", userService.page(query));
    }

    /**
     * 启用/禁用账号（教师管理学生用）
     * PUT /api/sys-user/status  body: {"id": 2, "status": 0}
     */
    @PutMapping("/status")
    public Result<Void> updateStatus(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        Integer status = Integer.valueOf(body.get("status").toString());
        userService.updateStatus(id, status);
        return Result.success("状态已更新", null);
    }

    /**
     * 重置密码（前端把明文 BCrypt 哈希后传过来）
     * PUT /api/sys-user/password  body: {"id": 2, "password": "$2a$10$..."}
     */
    @PutMapping("/password")
    public Result<Void> updatePassword(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        String bcrypt = (String) body.get("password");
        userService.updatePassword(id, bcrypt);
        return Result.success("密码已重置", null);
    }

    /**
     * 调整账号所属班级（管理员调班用）
     * PUT /api/sys-user/class  body: {"id": 2, "classId": 16}
     * classId 传 null 表示取消班级归属
     */
    @PutMapping("/class")
    public Result<Void> updateClass(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        Object cid = body.get("classId");
        Long classId = (cid == null || cid.toString().isEmpty()) ? null : Long.valueOf(cid.toString());
        userService.updateClassId(id, classId);
        return Result.success("调班成功", null);
    }

    /**
     * 新增账号（管理员用）
     * POST /api/sys-user/create  body: SysUser（username/realName/role 必填，password/classId/phone 选填）
     */
    @PostMapping("/create")
    public Result<SysUser> createUser(@RequestBody SysUser user) {
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return Result.error(400, "账号（学号）不能为空");
        }
        if (user.getRealName() == null || user.getRealName().trim().isEmpty()) {
            return Result.error(400, "姓名不能为空");
        }
        if (user.getRole() == null) {
            return Result.error(400, "请选择角色");
        }
        try {
            SysUser created = userService.createUser(user);
            created.setPassword(null); // 不回传密码
            return Result.success("创建成功", created);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 批量新增账号（管理员用）
     * POST /api/sys-user/createBatch  body: SysUser 数组
     * 每条复用单人创建逻辑（密码留空默认 123456）；账号重复/字段缺失的自动跳过
     */
    @PostMapping("/createBatch")
    public Result<Map<String, Integer>> createBatch(@RequestBody List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return Result.error(400, "请至少填写一条账号信息");
        }
        if (users.size() > 200) {
            return Result.error(400, "单次最多批量新增 200 个账号");
        }
        return Result.success("批量新增完成", userService.createBatch(users));
    }

    /**
     * 批量删除账号（管理员用）
     * DELETE /api/sys-user/batch?ids=1,2,3
     * admin 账号在 SQL 层强制保护；管理员账号至少保留一个，不满足时自动跳过（部分失败策略）
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/batch")
    public Result<java.util.Map<String, Object>> deleteBatch(@RequestParam String ids) {
        List<Long> idList = new java.util.ArrayList<>();
        for (String s : ids.split(",")) {
            if (!s.trim().isEmpty()) {
                try {
                    idList.add(Long.valueOf(s.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (idList.isEmpty()) {
            return Result.error(400, "请选择要删除的账号");
        }
        java.util.Map<String, Object> result = userService.deleteBatch(idList);
        int success = (int) result.get("success");
        int skipped = (int) result.get("skipped");
        String msg = "删除完成：成功 " + success + " 个";
        if (skipped > 0) {
            msg += "，跳过 " + skipped + " 个（受保护账号）";
        }
        return Result.success(msg, result);
    }

    /**
     * 批量校验学号/账号是否已存在（批量新增弹窗的实时提醒用）
     * POST /api/sys-user/usernamesExist  body: ["2300580101", ...]
     * @return 已存在的账号名列表
     */
    @org.springframework.web.bind.annotation.PostMapping("/usernamesExist")
    public Result<java.util.List<String>> usernamesExist(@RequestBody java.util.List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Result.success(java.util.Collections.emptyList());
        }
        // 去空、去重、截断（最多 200 个）
        java.util.List<String> cleaned = usernames.stream()
                .filter(u -> u != null && !u.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .limit(200)
                .collect(java.util.stream.Collectors.toList());
        if (cleaned.isEmpty()) {
            return Result.success(java.util.Collections.emptyList());
        }
        return Result.success(userService.findExistingUsernames(cleaned));
    }

    /**
     * Excel 批量导入账号（管理员用）
     * POST /api/sys-user/importExcel  form-data: file=xxx.xlsx
     * 列：账号/学号 | 姓名 | 角色(0学生/1教师/2管理员) | 班级名(可选) | 手机号(可选) | 邮箱(可选)
     */
    @PostMapping("/importExcel")
    public Result<Map<String, Object>> importExcel(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error(400, "请选择 Excel 文件");
        }
        String name = file.getOriginalFilename();
        if (name == null || (!name.endsWith(".xlsx") && !name.endsWith(".xls"))) {
            return Result.error(400, "请上传 .xlsx 格式的 Excel 文件");
        }
        try {
            Map<String, Object> result = userService.importExcel(file);
            int success = (int) result.get("success");
            int failed = (int) result.get("failed");
            String msg = "导入完成：成功 " + success + " 个";
            if (failed > 0) {
                msg += "，失败 " + failed + " 个";
            }
            return Result.success(msg, result);
        } catch (Exception e) {
            return Result.error(500, "导入失败：" + e.getMessage());
        }
    }
}
