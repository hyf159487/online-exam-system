package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.ExamQuery;
import com.exam.exam_system.entity.Exam;
import com.exam.exam_system.service.ExamService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 考试 Controller
 *
 * <p>接口清单：
 * <ul>
 *     <li>GET    /api/exam/page              分页查询（名称模糊 + 运行状态筛选）</li>
 *     <li>GET    /api/exam/{id}              按 ID 查详情</li>
 *     <li>POST   /api/exam                   发布考试</li>
 *     <li>PUT    /api/exam                   更新考试</li>
 *     <li>PUT    /api/exam/status            发布 / 撤回</li>
 *     <li>DELETE /api/exam/{id}              删除考试</li>
 *     <li>GET    /api/exam/count             考试总数（仪表盘）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/exam")
public class ExamController {

    @Autowired
    private ExamService examService;

    /** 教师数据权限：考试按「任教课程」隔离 */
    @Autowired
    private com.exam.exam_system.common.TeacherScope teacherScope;

    /**
     * 分页查询考试列表
     * GET /api/exam/page?pageNum=1&pageSize=10&keyword=期中&runtimeStatus=1
     * runtimeStatus：0未开始 1进行中 2已结束 3已撤回（不传查全部）
     */
    @GetMapping("/page")
    public Result<PageResult<Exam>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer runtimeStatus,
            @RequestParam(required = false) Long courseId,
            HttpServletRequest request) {

        ExamQuery query = new ExamQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setKeyword(keyword);
        query.setRuntimeStatus(runtimeStatus);
        query.setCourseId(courseId);
        // 数据权限：教师只能看到自己任教课程下的考试（管理员/其他角色不额外限制，学生端走 /api/student/**）
        if (teacherScope.isTeacher(request)) {
            query.setCourseIds(teacherScope.courseIdsOf(teacherScope.userId(request)));
        }

        return Result.success("查询成功", examService.page(query));
    }

    /** GET /api/exam/{id} */
    @GetMapping("/{id}")
    public Result<Exam> getById(@PathVariable Long id, HttpServletRequest request) {
        Exam exam = examService.getById(id);
        if (exam == null) {
            return Result.error(404, "考试不存在");
        }
        // 数据权限：教师不能查看别人任教课程下的考试
        if (teacherScope.isTeacher(request)) {
            teacherScope.checkCourse(request, exam.getCourseId());
        }
        return Result.success("查询成功", exam);
    }

    /**
     * POST /api/exam 发布考试
     *
     * <p>前端弹窗不提供「学科」项：考试的学科 = 所选试卷的学科（Service 会从试卷快照 courseId），
     * 因此这里先按试卷解析出 courseId 再做数据权限校验，不能要求请求体直接带 courseId。
     */
    @PostMapping
    public Result<Void> add(@RequestBody Exam exam, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (exam.getPaperId() == null) {
            return Result.error(400, "必须选择一张试卷");
        }
        Long courseId = examService.resolveCourseIdByPaperId(exam.getPaperId());
        if (courseId == null) {
            return Result.error(400, "所选试卷不存在");
        }
        // 数据权限：只能把考试发布到自己任教的课程下
        teacherScope.checkCourse(request, courseId);
        try {
            examService.add(exam, userId);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
        return Result.success("发布成功", null);
    }

    /** PUT /api/exam 更新考试 */
    @PutMapping
    public Result<Void> update(@RequestBody Exam exam, HttpServletRequest request) {
        if (exam.getId() == null) {
            return Result.error(400, "考试ID不能为空");
        }
        // 数据权限①：原考试必须在自己任教的课程下
        checkExamWritable(exam.getId(), request);
        // 数据权限②：不允许把考试改挂到别人任教的课程下。
        // 前端不传 courseId（考试学科跟随试卷），所以换试卷时按「新试卷的学科」校验
        Long targetCourseId = exam.getPaperId() != null
                ? examService.resolveCourseIdByPaperId(exam.getPaperId())
                : exam.getCourseId();
        if (targetCourseId != null) {
            teacherScope.checkCourse(request, targetCourseId);
        }
        try {
            examService.update(exam);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
        return Result.success("更新成功", null);
    }

    /** PUT /api/exam/status 发布 / 撤回，请求体 {id, status}，status: 1=发布 0=撤回 */
    @PutMapping("/status")
    public Result<Void> updateStatus(@RequestBody Exam body, HttpServletRequest request) {
        if (body.getId() == null) {
            return Result.error(400, "考试ID不能为空");
        }
        checkExamWritable(body.getId(), request);
        try {
            examService.updateStatus(body.getId(), body.getStatus());
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
        return Result.success("操作成功", null);
    }

    /** PUT /api/exam/{id}/finish 提前结束：结束时间改为当前时刻，考试立即进入"已结束" */
    @PutMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id, HttpServletRequest request) {
        checkExamWritable(id, request);
        try {
            examService.finishNow(id);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
        return Result.success("考试已提前结束", null);
    }

    /**
     * PUT /api/exam/{id}/republish 重新发布已结束的考试，两种模式二选一：
     * body: {"days": 3}                                    → 结束时间 = 当前时间 + 3 天
     * body: {"startTime": "...", "endTime": "..."}          → 自定义时间窗口（格式 yyyy-MM-dd HH:mm:ss）
     */
    @PutMapping("/{id}/republish")
    public Result<Void> republish(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body,
                                  HttpServletRequest request) {
        checkExamWritable(id, request);
        Integer days = null;
        java.time.LocalDateTime startTime = null;
        java.time.LocalDateTime endTime = null;
        try {
            if (body.get("days") != null && !body.get("days").toString().isBlank()) {
                days = Integer.valueOf(body.get("days").toString());
            }
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            if (body.get("startTime") != null && !body.get("startTime").toString().isBlank()) {
                startTime = java.time.LocalDateTime.parse(body.get("startTime").toString(), fmt);
            }
            if (body.get("endTime") != null && !body.get("endTime").toString().isBlank()) {
                endTime = java.time.LocalDateTime.parse(body.get("endTime").toString(), fmt);
            }
            examService.republish(id, days, startTime, endTime);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (java.time.format.DateTimeParseException e) {
            return Result.error(400, "时间格式不正确，应为 yyyy-MM-dd HH:mm:ss");
        }
        return Result.success("重新发布成功", null);
    }

    /** DELETE /api/exam/{id} */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        checkExamWritable(id, request);
        examService.delete(id);
        return Result.success("删除成功", null);
    }

    /** GET /api/exam/count 考试总数（仪表盘；教师只统计自己任教课程下的考试） */
    @GetMapping("/count")
    public Result<Long> count(HttpServletRequest request) {
        java.util.List<Long> courseIds = teacherScope.isTeacher(request)
                ? teacherScope.courseIdsOf(teacherScope.userId(request))
                : null;
        return Result.success("查询成功", examService.countAll(courseIds));
    }

    /**
     * 内部方法：校验当前登录教师是否有权操作这场考试（管理员的写操作已由拦截器放行）。
     *
     * <p>用于「改/删/发布/撤回/提前结束/重新发布」这类只给了 examId 的接口，
     * 先按 id 查出考试所属课程，再交给 {@link com.exam.exam_system.common.TeacherScope} 判定归属。
     * 越权时抛 ForbiddenException/BizException，由全局异常处理器转成 403/400。
     */
    private void checkExamWritable(Long examId, HttpServletRequest request) {
        if (!teacherScope.isTeacher(request)) {
            return; // 管理员（或其它角色）由拦截器负责，这里不重复判权
        }
        Exam exist = examService.getById(examId);
        if (exist == null) {
            throw new com.exam.exam_system.common.BizException("考试不存在（id=" + examId + "）");
        }
        teacherScope.checkCourse(request, exist.getCourseId());
    }
}
