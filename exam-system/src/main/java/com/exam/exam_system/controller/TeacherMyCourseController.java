package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.common.TeacherScope;
import com.exam.exam_system.entity.Course;
import com.exam.exam_system.service.CourseApplyService;
import com.exam.exam_system.service.CourseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 教师端：「我的课程」——只展示当前登录教师任教的课程
 *
 * <p>接口清单：
 * <ul>
 *     <li>GET    /api/teacher/my-course/list                             我任教的课程（含学生人数）</li>
 *     <li>GET    /api/teacher/my-course/{courseId}                      课程信息（详情页展示：介绍字段 + 建设统计）</li>
 *     <li>PUT    /api/teacher/my-course/{courseId}                      修改课程信息（编码与任课教师由管理员维护）</li>
 *     <li>GET    /api/teacher/my-course/{courseId}/students             该课程已加入学生名单</li>
 *     <li>DELETE /api/teacher/my-course/{courseId}/students/{studentId} 将学生移出该课程</li>
 * </ul>
 *
 * <p>所有接口都会校验当前教师确实任教该课程，防止越权查看/操作他人课程的学生与课程信息。
 */
@RestController
@RequestMapping("/api/teacher/my-course")
public class TeacherMyCourseController {

    @Autowired
    private CourseApplyService courseApplyService;

    /** 课程信息读/写：与课程管理共用同一套 Service，写操作按教师权限裁剪字段 */
    @Autowired
    private CourseService courseService;

    /** 教师任教关系校验（管理员恒通过） */
    @Autowired
    private TeacherScope teacherScope;

    /**
     * 我的课程列表（按课程名称/编码、课程类型模糊筛选）
     * GET /api/teacher/my-course/list?keyword=xx&courseType=xx
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String courseType,
                                                  HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("userId");
        return Result.success("查询成功", courseApplyService.listMyCourses(teacherId, keyword, courseType));
    }

    /**
     * 课程信息（教师端「我的课程」点击课程卡片进入）
     * GET /api/teacher/my-course/{courseId}
     *
     * <p>返回结构与管理员端课程详情一致：{course, teachers, questionCount, paperCount, examCount, knowledgeCount}，
     * 保证两端共用一个「课程信息」页面组件。
     */
    @GetMapping("/{courseId}")
    public Result<Map<String, Object>> detail(@PathVariable Long courseId, HttpServletRequest request) {
        if (!teacherScope.canAccessCourse(request, courseId)) {
            return Result.error(403, "无权查看：该课程不是您任教的课程");
        }
        try {
            return Result.success("查询成功", courseService.getDetail(courseId));
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }

    /**
     * 修改课程信息（教师端课程信息页的「编辑」保存）
     * PUT /api/teacher/my-course/{courseId}
     *
     * <p>可改：课程名称 / 课程类型 / 简介 / 详细介绍 / 学习目标 / 适用人群 / 课程大纲 / 封面 / 开课时间 / 报名截止；
     * 课程编码与任课教师绑定由管理员维护，提交时后端会忽略。
     */
    @PutMapping("/{courseId}")
    public Result<Void> update(@PathVariable Long courseId, @RequestBody Course course,
                               HttpServletRequest request) {
        if (!teacherScope.canAccessCourse(request, courseId)) {
            return Result.error(403, "无权修改：该课程不是您任教的课程");
        }
        if (course == null) {
            return Result.error(400, "缺少课程信息");
        }
        course.setId(courseId);
        try {
            courseService.updateByTeacher(course);
            return Result.success("课程信息已更新", null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 课程学生名单（按学生姓名/学号模糊筛选）
     * GET /api/teacher/my-course/{courseId}/students?keyword=xx
     */
    @GetMapping("/{courseId}/students")
    public Result<List<Map<String, Object>>> students(@PathVariable Long courseId,
                                                      @RequestParam(required = false) String keyword,
                                                      HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("userId");
        try {
            return Result.success("查询成功", courseApplyService.listCourseStudents(teacherId, courseId, keyword));
        } catch (IllegalArgumentException e) {
            return Result.error(403, e.getMessage());
        }
    }

    /**
     * 将学生移出课程
     * DELETE /api/teacher/my-course/{courseId}/students/{studentId}
     */
    @DeleteMapping("/{courseId}/students/{studentId}")
    public Result<Void> removeStudent(@PathVariable Long courseId,
                                      @PathVariable Long studentId,
                                      HttpServletRequest request) {
        Long teacherId = (Long) request.getAttribute("userId");
        try {
            courseApplyService.removeStudent(teacherId, courseId, studentId);
            return Result.success("已将该学生移出课程", null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
