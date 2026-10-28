package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.AiScoreRequest;
import com.exam.exam_system.dto.AiScoreResult;
import com.exam.exam_system.dto.ReviewWaitVO;
import com.exam.exam_system.dto.SaveScoreRequest;
import com.exam.exam_system.entity.ExamAnswer;
import com.exam.exam_system.service.AiScoreService;
import com.exam.exam_system.service.ExamReviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 主观题阅卷 Controller
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET    /api/review/wait-list         分页查询主观题阅卷列表（待阅/已阅）</li>
 *   <li>GET    /api/review/answer/{answerId} 获取单条答题详情</li>
 *   <li>POST   /api/review/save-score       提交阅卷得分和评语</li>
 *   <li>POST   /api/review/ai-score         AI 预评分（单题，不落库，教师确认后再保存）</li>
 *   <li>POST   /api/review/ai-score-batch   AI 预评分（整卷多题，单次最多 10 题）</li>
 * </ul>
 *
 * <p>权限：
 * <ul>
 *   <li>教师(role=1)：只能批阅自己创建考试下的主观题</li>
 *   <li>管理员(role=2)：可以批阅全部考试主观题</li>
 *   <li>学生(role=0)：无权限</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/review")
public class ExamReviewController {

    @Autowired
    private ExamReviewService reviewService;

    @Autowired
    private AiScoreService aiScoreService;

    /**
     * 分页查询主观题阅卷列表
     */
    @GetMapping("/wait-list")
    public Result<PageResult<ReviewWaitVO>> waitList(
            @RequestParam(required = false) Long examId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Integer reviewStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "10") Long pageSize,
            HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        Long userId = (Long) request.getAttribute("userId");
        PageResult<ReviewWaitVO> result = reviewService.listSubjectiveAnswers(
                examId, classId, reviewStatus, keyword, userId, role, pageNum, pageSize);
        return Result.success("查询成功", result);
    }

    /**
     * 获取单条答题详情
     */
    @GetMapping("/answer/{answerId}")
    public Result<ExamAnswer> answerDetail(@PathVariable Long answerId,
                                            HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        Long userId = (Long) request.getAttribute("userId");
        try {
            ExamAnswer answer = reviewService.getAnswerDetail(answerId, userId, role);
            return Result.success("查询成功", answer);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 提交阅卷得分和评语
     */
    @PostMapping("/save-score")
    public Result<Void> saveScore(@RequestBody SaveScoreRequest req,
                                   HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可操作");
        }
        Long userId = (Long) request.getAttribute("userId");
        try {
            reviewService.saveScore(req, userId, role);
            return Result.success("阅卷保存成功", null);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * AI 预评分（单题）
     *
     * <p>返回建议得分、学生可见评语、教师可见评分依据与置信度，<b>不落库</b>；
     * 教师确认或调整后调用 /api/review/save-score 保存。
     */
    @PostMapping("/ai-score")
    public Result<AiScoreResult> aiScore(@RequestBody AiScoreRequest req,
                                         HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可操作");
        }
        Long userId = (Long) request.getAttribute("userId");
        try {
            AiScoreResult result = aiScoreService.score(req.getAnswerId(), userId, role);
            return Result.success("AI 预评分完成（" + sourceText(result) + "）", result);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * AI 预评分（整卷多题一键预评，单次最多 10 题）
     */
    @PostMapping("/ai-score-batch")
    public Result<List<AiScoreResult>> aiScoreBatch(@RequestBody AiScoreRequest req,
                                                    HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可操作");
        }
        Long userId = (Long) request.getAttribute("userId");
        try {
            List<AiScoreResult> list = aiScoreService.scoreBatch(req.getAnswerIds(), userId, role);
            long llmCount = list.stream().filter(r -> "llm".equals(r.getSource())).count();
            String message = llmCount > 0
                    ? "AI 已预评 " + list.size() + " 道题（其中 " + llmCount + " 道由大模型评分）"
                    : "AI 已预评 " + list.size() + " 道题（本地规则引擎）";
            return Result.success(message, list);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 结果来源展示文案 */
    private String sourceText(AiScoreResult result) {
        return result != null && "llm".equals(result.getSource()) ? "大模型" : "本地规则引擎";
    }
}
