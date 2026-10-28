package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.MonitorStudentVO;
import com.exam.exam_system.dto.RunningExamVO;
import com.exam.exam_system.dto.StudentAnswerDetailVO;
import com.exam.exam_system.service.ExamMonitorService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 考试监控 Controller
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET  /api/monitor/running-exams         获取所有正在进行的考试</li>
 *   <li>GET  /api/monitor/students/{examId}    获取某场考试的学生监控列表（分页）</li>
 *   <li>GET  /api/monitor/answer-detail        查看学生答题详情</li>
 *   <li>POST /api/monitor/force-submit         强制交卷</li>
 * </ul>
 *
 * <p>权限：
 * <ul>
 *   <li>教师(role=1)：只能监控自己创建的考试，可强制交卷</li>
 *   <li>管理员(role=2)：监控全部考试，可强制交卷</li>
 *   <li>学生(role=0)：无权限</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/monitor")
public class ExamMonitorController {

    @Autowired
    private ExamMonitorService monitorService;

    /** 教师数据权限：监控数据按「任教课程」隔离 */
    @Autowired
    private com.exam.exam_system.common.TeacherScope teacherScope;

    /** 用于按 id 查考试所属课程，做监控接口的归属校验 */
    @Autowired
    private com.exam.exam_system.mapper.ExamMapper examMapper;

    /**
     * 获取所有正在进行的考试
     */
    @GetMapping("/running-exams")
    public Result<List<RunningExamVO>> listRunningExams(HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        // 数据权限：教师只能看到自己任教课程下的考试；管理员返回 null（不限制）
        List<RunningExamVO> exams = monitorService.listRunningExams(teacherScope.myCourseIds(request));
        return Result.success("查询成功", exams);
    }

    /**
     * 获取某场考试的学生监控列表（分页）
     */
    @GetMapping("/students/{examId}")
    public Result<PageResult<MonitorStudentVO>> listStudents(
            @PathVariable Long examId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "10") Long pageSize,
            HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        // 数据权限：教师只能监控自己任教课程下的考试
        checkExamScope(examId, request);
        PageResult<MonitorStudentVO> result = monitorService.listStudents(
                examId, classId, status, keyword, pageNum, pageSize);
        return Result.success("查询成功", result);
    }

    /**
     * 查看学生答题详情
     */
    @GetMapping("/answer-detail")
    public Result<List<StudentAnswerDetailVO>> answerDetail(
            @RequestParam Long examId,
            @RequestParam Long studentId,
            HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        // 数据权限：教师只能查看自己任教课程下考试的答题详情
        checkExamScope(examId, request);
        List<StudentAnswerDetailVO> details = monitorService.listStudentAnswers(examId, studentId);
        return Result.success("查询成功", details);
    }

    /**
     * 强制交卷
     */
    @PostMapping("/force-submit")
    public Result<Void> forceSubmit(@RequestBody ForceSubmitRequest req,
                                     HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可操作");
        }
        if (req.getExamId() == null || req.getStudentId() == null) {
            return Result.error(400, "参数不完整");
        }
        // 数据权限：教师只能对自己任教课程下的考试强制交卷
        checkExamScope(req.getExamId(), request);
        Long userId = (Long) request.getAttribute("userId");
        try {
            monitorService.forceSubmit(req.getExamId(), req.getStudentId(), userId, role);
            return Result.success("强制交卷成功", null);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 数据权限校验：教师只能操作自己任教课程下的考试监控数据（管理员放行）。
     *
     * <p>比起"按 creator_id 过滤"，按课程判定更符合业务：
     * 同一门课由多位教师协作时，任课教师都应能看到该课程的考试监控。
     */
    private void checkExamScope(Long examId, HttpServletRequest request) {
        if (!teacherScope.isTeacher(request)) {
            return;
        }
        com.exam.exam_system.entity.Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new com.exam.exam_system.common.BizException("考试不存在（id=" + examId + "）");
        }
        teacherScope.checkCourse(request, exam.getCourseId());
    }

    /**
     * 强制交卷请求体
     */
    @lombok.Data
    public static class ForceSubmitRequest {
        private Long examId;
        private Long studentId;
    }
}
