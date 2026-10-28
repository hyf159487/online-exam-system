package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.entity.SysClass;
import com.exam.exam_system.service.SysClassService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 班级管理 Controller（管理员）
 *
 * <p>接口清单：
 * <ul>
 *     <li>GET    /api/class            班级列表（含学生数）</li>
 *     <li>POST   /api/class            新增班级</li>
 *     <li>PUT    /api/class/{id}       更新班级</li>
 *     <li>DELETE /api/class/{id}       删除班级（班级下仍有学生时拒绝）</li>
 * </ul>
 *
 * <p>仅管理员可增删改；查询列表教师/管理员均可查看。
 */
@RestController
@RequestMapping("/api/class")
public class SysClassController {

    @Autowired
    private SysClassService sysClassService;

    @Autowired
    private com.exam.exam_system.service.UserService userService;

    /** 班级列表（含学生数） */
    @GetMapping
    public Result<List<SysClass>> list(HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role < 1) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        return Result.success("查询成功", sysClassService.listAllWithCount());
    }

    /**
     * 指定班级下的学生列表（点击【查看】跳转班级学生详情页调用）
     * GET /api/class/studentList?classId=16
     * 教师/管理员均可查看；调班/移出操作走 /api/student/* 接口（仅管理员）
     */
    @GetMapping("/studentList")
    public Result<List<com.exam.exam_system.entity.SysUser>> studentList(
            @org.springframework.web.bind.annotation.RequestParam Long classId,
            HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role < 1) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        return Result.success("查询成功", userService.listByClassId(classId));
    }

    /** 新增班级（仅管理员） */
    @PostMapping
    public Result<SysClass> create(@RequestBody SysClass sysClass, HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 2) {
            return Result.error(403, "仅管理员可操作");
        }
        return Result.success("新增成功", sysClassService.create(sysClass));
    }

    /** 更新班级（仅管理员） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysClass sysClass,
                               HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 2) {
            return Result.error(403, "仅管理员可操作");
        }
        sysClass.setId(id);
        sysClassService.update(sysClass);
        return Result.success("更新成功", null);
    }

    /** 删除班级（仅管理员，班级下仍有学生时拒绝） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 2) {
            return Result.error(403, "仅管理员可操作");
        }
        sysClassService.delete(id);
        return Result.success("删除成功", null);
    }

    /**
     * 批量新增班级（仅管理员）
     * POST /api/class/batch  body: [{className, grade, department, major}, ...]
     * 部分失败策略：空名/批内重复/库内重复逐条跳过，返回成功/失败明细
     */
    @PostMapping("/batch")
    public Result<java.util.Map<String, Object>> createBatch(@RequestBody List<SysClass> classes,
                                                             HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 2) {
            return Result.error(403, "仅管理员可操作");
        }
        java.util.Map<String, Object> result = sysClassService.createBatch(classes);
        int success = (int) result.get("success");
        int failed = (int) result.get("failed");
        String msg = "批量新增完成：成功 " + success + " 个";
        if (failed > 0) {
            msg += "，失败 " + failed + " 个";
        }
        return Result.success(msg, result);
    }

    /**
     * Excel 批量导入班级（仅管理员）
     * POST /api/class/importExcel  form-data: file=xxx.xlsx
     * 列：班级名称 | 年级 | 院系 | 专业（第一行表头跳过）
     */
    @PostMapping("/importExcel")
    public Result<java.util.Map<String, Object>> importExcel(
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || role != 2) {
            return Result.error(403, "仅管理员可操作");
        }
        if (file == null || file.isEmpty()) {
            return Result.error(400, "请选择 Excel 文件");
        }
        String name = file.getOriginalFilename();
        if (name == null || (!name.endsWith(".xlsx") && !name.endsWith(".xls"))) {
            return Result.error(400, "请上传 .xlsx 格式的 Excel 文件");
        }
        try {
            java.util.Map<String, Object> result = sysClassService.importExcel(file);
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
