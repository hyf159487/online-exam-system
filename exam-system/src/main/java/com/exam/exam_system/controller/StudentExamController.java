package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.AiChatRequest;
import com.exam.exam_system.dto.AiExplainResult;
import com.exam.exam_system.dto.StudentExamDetail;
import com.exam.exam_system.dto.StudentRecordDetail;
import com.exam.exam_system.dto.SubmitRequest;
import com.exam.exam_system.dto.SubmitResult;
import com.exam.exam_system.entity.Exam;
import com.exam.exam_system.entity.ExamRecord;
import com.exam.exam_system.service.StudentExamService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生端考试 Controller
 *
 * <p>所有接口都要带 token（AuthInterceptor 已统一拦截 /api/**），
 * 当前学生 userId 从 token 解析，放 request 属性里拿——学生没法冒充别人交卷。
 */
@RestController
@RequestMapping("/api/student")
public class StudentExamController {

    @Autowired
    private StudentExamService studentExamService;

    /** 我的考试列表（全部已发布考试，前端按状态展示） */
    @GetMapping("/exams")
    public Result<List<Exam>> myExams(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(studentExamService.listExamsForStudent(userId));
    }

    /** 考试详情 + 题目（不含答案） */
    @GetMapping("/exams/{id}/questions")
    public Result<StudentExamDetail> examQuestions(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            return Result.success(studentExamService.getExamDetail(id, userId));
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 保存答题进度（刷新/断线后恢复作答，不判分） */
    @PostMapping("/exams/{id}/progress")
    public Result<Void> saveProgress(@PathVariable Long id,
                                     @RequestBody SubmitRequest req,
                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            studentExamService.saveProgress(id, req, userId);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 交卷（客观题自动判分） */
    @PostMapping("/exams/{id}/submit")
    public Result<SubmitResult> submit(@PathVariable Long id,
                                       @RequestBody SubmitRequest req,
                                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            return Result.success("交卷成功", studentExamService.submit(id, req, userId));
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 我的成绩 */
    @GetMapping("/records")
    public Result<List<ExamRecord>> myRecords(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(studentExamService.myRecords(userId));
    }

    /**
     * 查看我的某次试卷（教师批阅完成后学生可回看）
     *
     * <p>返回逐题作答、得分、教师评语与批阅进度；参考答案与解析在考试结束后才下发。
     * 只能查看自己的记录（按当前登录 userId 校验）。</p>
     */
    @GetMapping("/records/{recordId}")
    public Result<StudentRecordDetail> recordDetail(@PathVariable Long recordId,
                                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            return Result.success(studentExamService.getRecordDetail(recordId, userId));
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 试卷回看里的 AI 讲解：对某道题生成讲解
     *
     * <p>门槛与查看试卷一致（本人记录 + 已交卷 + 考试已结束），考试没结束时返回 400。
     * 讲解上下文含题干、选项、我的作答与参考答案；未配置大模型 api-key 时降级为本地规则引擎。</p>
     */
    @PostMapping("/records/{recordId}/questions/{questionId}/explain")
    public Result<AiExplainResult> explainQuestion(@PathVariable Long recordId,
                                                   @PathVariable Long questionId,
                                                   HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            return Result.success("生成成功",
                    studentExamService.explainRecordQuestion(userId, recordId, questionId));
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 试卷回看里的 AI 追问：携带历史对话继续提问（本地规则引擎仅支持首轮） */
    @PostMapping("/records/{recordId}/questions/{questionId}/chat")
    public Result<AiExplainResult> chatQuestion(@PathVariable Long recordId,
                                                @PathVariable Long questionId,
                                                @RequestBody AiChatRequest req,
                                                HttpServletRequest request) {
        if (req == null || req.getQuestion() == null || req.getQuestion().isBlank()) {
            return Result.error(400, "请输入你的问题");
        }
        Long userId = (Long) request.getAttribute("userId");
        try {
            return Result.success("ok", studentExamService.chatRecordQuestion(
                    userId, recordId, questionId, req.getQuestion(), req.getHistory()));
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
