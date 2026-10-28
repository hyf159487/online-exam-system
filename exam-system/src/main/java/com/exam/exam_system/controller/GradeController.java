package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.GradeQuery;
import com.exam.exam_system.dto.GradeStats;
import com.exam.exam_system.dto.ReviewDetail;
import com.exam.exam_system.dto.ReviewSubmitRequest;
import com.exam.exam_system.entity.ExamRecord;
import com.exam.exam_system.service.GradeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成绩管理 Controller
 *
 * <p>接口清单：
 * <ul>
 *     <li>GET    /api/grade/page     分页查询成绩（试卷筛选 + 学号/姓名模糊）</li>
 *     <li>GET    /api/grade/stats    成绩统计（人数/平均/最高/最低/及格数/分数段分布）</li>
 *     <li>GET    /api/grade/pending  待阅卷列表（status=1 已交卷、简答题待教师判分）</li>
 *     <li>GET    /api/grade/{recordId}/review   阅卷详情（逐题题干/标准答案/学生作答）</li>
 *     <li>POST   /api/grade/{recordId}/review   教师提交判分（写简答题得分与评语，重算总分）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/grade")
public class GradeController {

    @Autowired
    private GradeService gradeService;

    /** 教师数据权限：成绩查询/统计/阅卷一律按「任教课程」隔离 */
    @Autowired
    private com.exam.exam_system.common.TeacherScope teacherScope;

    /**
     * GET /api/grade/page?pageNum=1&pageSize=10&paperId=1&keyword=张
     *
     * <p>数据权限：教师只能看到自己任教课程下的成绩（管理员看全部）
     */
    @GetMapping("/page")
    public Result<PageResult<ExamRecord>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long paperId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {

        GradeQuery query = new GradeQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setPaperId(paperId);
        query.setCourseId(courseId);
        query.setKeyword(keyword);
        // 数据权限：教师任教课程集合；管理员为 null（不限制）
        query.setCourseIds(teacherScope.myCourseIds(request));

        return Result.success("查询成功", gradeService.page(query));
    }

    /**
     * GET /api/grade/stats?paperId=1&courseId=2（不传 = 全部汇总）
     *
     * <p>数据权限：教师只统计自己任教课程下的成绩
     */
    @GetMapping("/stats")
    public Result<GradeStats> stats(
            @RequestParam(required = false) Long paperId,
            @RequestParam(required = false) Long courseId,
            HttpServletRequest request) {
        return Result.success("查询成功",
                gradeService.stats(paperId, courseId, teacherScope.myCourseIds(request)));
    }

    /**
     * 待阅卷列表
     * GET /api/grade/pending?pageNum=1&pageSize=10&paperId=1&keyword=张
     *
     * <p>数据权限：教师只能看到自己任教课程下的待阅卷记录
     */
    @GetMapping("/pending")
    public Result<PageResult<ExamRecord>> pending(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long paperId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {

        GradeQuery query = new GradeQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setPaperId(paperId);
        query.setCourseId(courseId);
        query.setKeyword(keyword);
        query.setCourseIds(teacherScope.myCourseIds(request));

        return Result.success("查询成功", gradeService.listPending(query));
    }

    /**
     * 阅卷详情
     * GET /api/grade/{recordId}/review
     */
    @GetMapping("/{recordId}/review")
    public Result<ReviewDetail> reviewDetail(@PathVariable Long recordId, HttpServletRequest request) {
        // 数据权限：不能阅别人任教课程下的卷
        checkRecordScope(recordId, request);
        try {
            return Result.success("查询成功", gradeService.reviewDetail(recordId));
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 教师提交判分
     * POST /api/grade/{recordId}/review
     * 请求体（JSON）：
     * {
     *   "items": [
     *     {"questionId": 10, "score": 8.0, "comment": "要点基本答对"}
     *   ]
     * }
     */
    @PostMapping("/{recordId}/review")
    public Result<Void> submitReview(@PathVariable Long recordId,
                                     @RequestBody ReviewSubmitRequest request,
                                     HttpServletRequest httpRequest) {
        // 数据权限：不能给别人任教课程下的卷判分
        checkRecordScope(recordId, httpRequest);
        try {
            gradeService.submitReview(recordId, request);
            return Result.success("判分成功", null);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 数据权限校验：教师只能操作自己任教课程下的考试记录（管理员放行）。
     *
     * <p>成绩挂在 exam_record → exam_paper.course_id 上，先取出记录所属课程再判定归属。
     */
    private void checkRecordScope(Long recordId, HttpServletRequest request) {
        if (!teacherScope.isTeacher(request)) {
            return;
        }
        Long courseId = gradeService.courseIdOfRecord(recordId);
        if (courseId == null) {
            throw new com.exam.exam_system.common.BizException("考试记录不存在或所属试卷已删除（id=" + recordId + "）");
        }
        teacherScope.checkCourse(request, courseId);
    }
}
