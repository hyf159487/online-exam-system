package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.entity.Course;
import com.exam.exam_system.entity.CourseApply;
import com.exam.exam_system.service.CourseApplyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 学生端：加入课程 / 我的申请
 *
 * <p>走 /api/student/**，由 AuthInterceptor 限制仅学生（role=0）可访问。
 */
@RestController
@RequestMapping("/api/student/course")
public class StudentCourseController {

    @Autowired
    private CourseApplyService courseApplyService;

    /**
     * 开放课程列表（含当前学生对每门课的状态），分页返回
     * GET /api/student/course/list?statusFilter=xx&courseName=xx&teacherName=yy&courseType=zz&pageNum=1&pageSize=10
     * statusFilter 可选：joined=已加入，notJoined=未加入，pending=审核中；不传=全部
     * courseType 可选：课程类型精确筛选；不传=全部
     */
    @GetMapping("/list")
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String statusFilter,
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) String teacherName,
            @RequestParam(required = false) String courseType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success("查询成功",
                courseApplyService.listOpenCourses(userId, statusFilter, courseName, teacherName,
                        courseType, pageNum, pageSize));
    }

    /**
     * 课程类型下拉选项（课程表中已有类型去重）
     * GET /api/student/course/types
     */
    @GetMapping("/types")
    public Result<List<String>> courseTypes() {
        return Result.success("查询成功", courseApplyService.listCourseTypes());
    }

    /**
     * 课程名称 / 授课教师筛选下拉候选（去重列表，供筛选框下拉选择 + 输入关键字模糊查询）
     * GET /api/student/course/filter-options
     */
    @GetMapping("/filter-options")
    public Result<Map<String, List<String>>> filterOptions() {
        return Result.success("查询成功", courseApplyService.filterOptions());
    }

    /**
     * 申请加入课程
     * POST /api/student/course/apply/{courseId}
     */
    @PostMapping("/apply/{courseId}")
    public Result<Void> apply(@PathVariable Long courseId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String realName = (String) request.getAttribute("realName");
        String userName = (String) request.getAttribute("userName");
        String studentName = (realName != null && !realName.isBlank()) ? realName : (userName != null ? userName : "学生");
        try {
            courseApplyService.apply(userId, courseId, studentName);
            return Result.success("申请已提交，请等待教师审核", null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 申请退出课程
     * POST /api/student/course/quit/{courseId}
     */
    @PostMapping("/quit/{courseId}")
    public Result<Void> quit(@PathVariable Long courseId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String realName = (String) request.getAttribute("realName");
        String userName = (String) request.getAttribute("userName");
        String studentName = (realName != null && !realName.isBlank()) ? realName : (userName != null ? userName : "学生");
        try {
            courseApplyService.applyQuit(userId, courseId, studentName);
            return Result.success("退出申请已提交，请等待教师审核", null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 我的申请记录
     * GET /api/student/course/my-applies
     */
    @GetMapping("/my-applies")
    public Result<List<CourseApply>> myApplies(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success("查询成功", courseApplyService.listMine(userId));
    }

    /**
     * 我已加入的课程列表（自主练习的学科下拉、练习卷/题库/资料过滤用）
     * GET /api/student/course/joined
     */
    @GetMapping("/joined")
    public Result<List<Course>> joined(@RequestParam(required = false) String keyword,
                                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success("查询成功", courseApplyService.listJoined(userId, keyword));
    }

    /**
     * 课程详情（学生端课程详情页顶部信息区）
     * GET /api/student/course/detail/{courseId}
     *
     * <p>返回课程完整信息（含详细介绍/学习目标/适用人群/课程大纲/任课教师/已加入人数）
     * 与当前学生关系状态（joined/inPending/canApply/quitPending/canQuit/applyClosed），
     * 前端据此锁定「课程资料 / 课程公告 / 课程作业 / 课程考试」Tab 并切换底部按钮。
     */
    @GetMapping("/detail/{courseId}")
    public Result<Map<String, Object>> detail(@PathVariable Long courseId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            return Result.success("查询成功", courseApplyService.getCourseDetail(userId, courseId));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 个人中心「我的课程」：我已加入的课程信息（含任课教师、参加人数、本人加入时间、资料数、退出审核状态）
     * GET /api/student/course/my-courses?keyword=xx
     */
    @GetMapping("/my-courses")
    public Result<List<Map<String, Object>>> myCourses(@RequestParam(required = false) String keyword,
                                                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success("查询成功", courseApplyService.listMyJoinedCourses(userId, keyword));
    }
}
