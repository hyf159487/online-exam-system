package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.QuestionQualityVO;
import com.exam.exam_system.dto.StatsExamVO;
import com.exam.exam_system.service.StatsQuestionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 试题质量统计 Controller
 */
@RestController
@RequestMapping("/api/stats/question")
public class StatsQuestionController {

    @Autowired
    private StatsQuestionService statsService;

    /** 教师数据权限：试题质量统计按考试的任教课程隔离 */
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
        List<StatsExamVO> list = statsService.getExamList(userId, role);
        return Result.success("查询成功", list);
    }

    /**
     * 获取某考试下所有试题质量指标
     */
    @GetMapping("/data")
    public Result<List<QuestionQualityVO>> questionData(@RequestParam Long examId,
                                                        HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        if (examId == null) {
            return Result.error(400, "请选择考试");
        }
        // 数据权限：教师只能统计自己任教课程下的考试
        checkExamScope(examId, request);
        try {
            List<QuestionQualityVO> data = statsService.getQuestionQualityList(examId);
            return Result.success("查询成功", data);
        } catch (Exception e) {
            return Result.error(500, "统计失败：" + e.getMessage());
        }
    }

    /**
     * 获取某题目的学生答题明细
     */
    @GetMapping("/answer-detail")
    public Result<QuestionQualityVO> answerDetail(@RequestParam Long examId,
                                                   @RequestParam Long questionId,
                                                   HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        // 数据权限：教师只能查看自己任教课程下考试的答题明细
        checkExamScope(examId, request);
        QuestionQualityVO detail = statsService.getAnswerDetail(examId, questionId);
        return Result.success("查询成功", detail);
    }

    /**
     * 数据权限校验：教师只能统计自己任教课程下的考试（管理员放行）。
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
}
