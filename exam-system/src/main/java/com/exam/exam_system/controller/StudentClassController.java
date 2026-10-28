package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 学生调班 Controller（双入口复用）
 *
 * <p>账号管理页（单人快捷调班）与班级管理页（单人/批量调班、移出本班）
 * 共用同一套后端接口，底层只操作 sys_user.class_id：
 * <ul>
 *     <li>PUT /api/student/updateClass   单人/批量调班（studentIds 数组 + targetClassId）</li>
 *     <li>PUT /api/student/removeClass   批量移出本班（class_id 置 null，账号保留）</li>
 * </ul>
 *
 * <p>权限：仅管理员（role=2）可调班/移出；教师只可查看班级学生，不允许调班。
 */
@RestController
@RequestMapping("/api/student")
public class StudentClassController {

    @Autowired
    private UserService userService;

    /**
     * 单人/批量调班（管理员）
     * PUT /api/student/updateClass
     * body: {"studentIds": [1,2,3], "targetClassId": 16}
     * 账号管理调用传单个 id；班级管理可传多个 id
     */
    @PutMapping("/updateClass")
    public Result<Void> updateClass(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 2) {
            return Result.error(403, "调班仅管理员可操作");
        }
        Object idsObj = body.get("studentIds");
        Object cidObj = body.get("targetClassId");
        if (!(idsObj instanceof List<?> rawIds) || rawIds.isEmpty()) {
            return Result.error(400, "请至少选择一名学生");
        }
        if (cidObj == null || cidObj.toString().isBlank()) {
            return Result.error(400, "请选择目标班级");
        }
        List<Long> studentIds = rawIds.stream().map(x -> Long.valueOf(x.toString())).toList();
        Long targetClassId = Long.valueOf(cidObj.toString());
        int moved = userService.updateClassBatch(studentIds, targetClassId);
        return Result.success("调班成功，共迁移 " + moved + " 名学生", null);
    }

    /**
     * 批量移出本班（管理员）
     * PUT /api/student/removeClass?studentIds=1,2,3
     * 本质是把学生 class_id 置 null，账号仍保留在系统账号管理中
     */
    @PutMapping("/removeClass")
    public Result<Void> removeClass(@RequestParam List<Long> studentIds, HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 2) {
            return Result.error(403, "移出本班仅管理员可操作");
        }
        if (studentIds == null || studentIds.isEmpty()) {
            return Result.error(400, "请至少选择一名学生");
        }
        int removed = userService.removeClassBatch(studentIds);
        return Result.success("移出成功，共移出 " + removed + " 名学生", null);
    }
}
