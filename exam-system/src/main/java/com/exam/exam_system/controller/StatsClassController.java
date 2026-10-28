package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.*;
import com.exam.exam_system.service.StatsClassService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 班级学情统计 Controller
 */
@RestController
@RequestMapping("/api/stats/class")
public class StatsClassController {

    @Autowired
    private StatsClassService statsService;

    /** 教师数据权限：班级学情统计按考试的任教课程隔离 */
    @Autowired
    private com.exam.exam_system.common.TeacherScope teacherScope;

    /** 用于按 examId 反查考试所属课程，做归属校验 */
    @Autowired
    private com.exam.exam_system.mapper.ExamMapper examMapper;

    /**
     * 获取可统计的考试列表
     */
    @GetMapping("/exam-list")
    public Result<List<StatsExamVO>> examList(HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        return Result.success("查询成功", statsService.getExamList(userId, role));
    }

    /**
     * 获取某考试下可统计的班级列表
     */
    @GetMapping("/class-list")
    public Result<List<ClassStatsQuery>> classList(@RequestParam Long examId,
                                                   HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        // 数据权限：教师只能统计自己任教课程下的考试
        checkExamScope(examId, request);
        try {
            return Result.success("查询成功",
                    statsService.getClassList(examId, (Long) request.getAttribute("userId"), role));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "加载班级列表失败：" + e.getMessage());
        }
    }

    /**
     * 获取班级学情统计数据
     */
    @PostMapping("/data")
    public Result<ClassStatsVO> classData(@RequestBody ClassStatsQuery query,
                                          HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        if (query.getExamId() == null || query.getClassId() == null) {
            return Result.error(400, "请选择考试与班级后再查询");
        }
        checkExamScope(query.getExamId(), request);
        query.setRole(role);
        query.setTeacherId((Long) request.getAttribute("userId"));
        try {
            return Result.success("查询成功", statsService.getClassStats(query));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "统计计算失败：" + e.getMessage());
        }
    }

    /**
     * 查看某学生该次考试逐题作答详情
     */
    @PostMapping("/answers")
    public Result<StudentAnswerVO> answers(@RequestBody ClassStatsQuery query,
                                           HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        if (query.getExamId() == null || query.getStudentId() == null) {
            return Result.error(400, "参数缺失：请选择考试与学生");
        }
        checkExamScope(query.getExamId(), request);
        try {
            Long teacherId = (Long) request.getAttribute("userId");
            return Result.success("查询成功",
                    statsService.getStudentAnswerDetail(query.getExamId(), query.getStudentId(),
                            teacherId, role));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "查询作答详情失败：" + e.getMessage());
        }
    }

    /**
     * 获取“作答维度统计”（按题型 type / 按难度 difficulty）
     */
    @PostMapping("/dimension")
    public Result<DimensionStatsVO> dimension(@RequestBody ClassStatsQuery query,
                                              @RequestParam(defaultValue = "difficulty") String mode,
                                              HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        if (query.getExamId() == null || query.getClassId() == null) {
            return Result.error(400, "参数缺失：请选择考试与班级");
        }
        checkExamScope(query.getExamId(), request);
        query.setRole(role);
        query.setTeacherId((Long) request.getAttribute("userId"));
        try {
            return Result.success("查询成功", statsService.getDimensionStats(query, mode));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "查询维度统计失败：" + e.getMessage());
        }
    }

    /**
     * 导出班级学情 Excel（指标 + 分数段 + 得分率 + 题型 + 难度 + 学生明细）
     */
    @PostMapping("/export")
    public void export(@RequestBody ClassStatsQuery query,
                       HttpServletResponse response,
                       HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        Long teacherId = (Long) request.getAttribute("userId");
        // 数据权限：越权时抛 403（响应体由全局异常处理器输出）
        checkExamScope(query.getExamId(), request);
        query.setRole(role);
        query.setTeacherId(teacherId);
        statsService.exportClassStatsExcel(query, response);
    }

    /**
     * 数据权限校验：教师只能统计自己任教课程下的考试（管理员放行）。
     */
    private void checkExamScope(Long examId, HttpServletRequest request) {
        if (!teacherScope.isTeacher(request) || examId == null) {
            return;
        }
        com.exam.exam_system.entity.Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new com.exam.exam_system.common.BizException("考试不存在（id=" + examId + "）");
        }
        teacherScope.checkCourse(request, exam.getCourseId());
    }
}
