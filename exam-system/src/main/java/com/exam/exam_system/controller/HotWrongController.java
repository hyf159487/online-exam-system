package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.HotWrongItem;
import com.exam.exam_system.dto.HotWrongQuery;
import com.exam.exam_system.dto.PaperQuestionItem;
import com.exam.exam_system.entity.ExamPaper;
import com.exam.exam_system.entity.PaperQuestion;
import com.exam.exam_system.mapper.ExamPaperMapper;
import com.exam.exam_system.mapper.HotWrongMapper;
import com.exam.exam_system.mapper.PaperQuestionMapper;
import com.exam.exam_system.service.HotWrongService;
import com.exam.exam_system.service.QuestionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 高频错题 Controller（管理员 + 教师可访问）
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET  /api/hot-wrong/list              高频错题分页列表</li>
 *   <li>GET  /api/hot-wrong/stat/{id}        单题错误率统计</li>
 *   <li>POST /api/hot-wrong/add-paper        将高频错题加入试卷</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/hot-wrong")
public class HotWrongController {

    @Autowired
    private HotWrongService hotWrongService;

    /** 教师数据权限：高频错题按「任教课程」隔离 */
    @Autowired
    private com.exam.exam_system.common.TeacherScope teacherScope;

    @Autowired
    private HotWrongMapper hotWrongMapper;

    @Autowired
    private ExamPaperMapper examPaperMapper;

    @Autowired
    private PaperQuestionMapper paperQuestionMapper;

    @Autowired
    private QuestionService questionService;

    /**
     * 高频错题分页列表
     */
    @GetMapping("/list")
    public Result<PageResult<HotWrongItem>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer questionType,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Double minErrorRate,
            @RequestParam(required = false) String sortBy,
            HttpServletRequest request) {

        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }

        HotWrongQuery query = new HotWrongQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setQuestionType(questionType);
        query.setDifficulty(difficulty);
        query.setCourseId(courseId);
        query.setMinErrorRate(minErrorRate);
        if (sortBy != null && !sortBy.isBlank()) {
            query.setSortBy(sortBy);
        }
        // 数据权限：教师只能统计自己任教课程下的题目错误率（管理员为 null，不限制）
        query.setCourseIds(teacherScope.myCourseIds(request));

        PageResult<HotWrongItem> result = hotWrongService.list(query);
        return Result.success("查询成功", result);
    }

    /**
     * 单题错误率统计
     */
    @GetMapping("/stat/{id}")
    public Result<HotWrongItem> stat(@PathVariable Long id, HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        // 数据权限：教师只能查看自己任教课程下题目的错误率
        if (role == 1) {
            var q = questionService.getById(id);
            if (q == null) {
                return Result.error(404, "题目不存在");
            }
            teacherScope.checkCourse(request, q.getCourseId());
        }
        HotWrongItem item = hotWrongService.statByQuestionId(id);
        return Result.success("查询成功", item);
    }

    /**
     * 将高频错题加入试卷
     * POST /api/hot-wrong/add-paper
     * Body: { "paperId": 1, "questionId": 10, "score": 5.0 }
     */
    @PostMapping("/add-paper")
    public Result<Void> addToPaper(@RequestBody Map<String, Object> body,
                                   HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }

        Long userId = (Long) request.getAttribute("userId");

        Long paperId = body.get("paperId") != null
                ? ((Number) body.get("paperId")).longValue() : null;
        Long questionId = body.get("questionId") != null
                ? ((Number) body.get("questionId")).longValue() : null;
        BigDecimal score = body.get("score") != null
                ? new BigDecimal(body.get("score").toString()) : null;

        if (paperId == null) {
            return Result.error(400, "试卷ID不能为空");
        }
        if (questionId == null) {
            return Result.error(400, "题目ID不能为空");
        }

        // 验证试卷存在且当前用户有权限
        ExamPaper paper = examPaperMapper.selectById(paperId);
        if (paper == null) {
            return Result.error(404, "试卷不存在");
        }
        if (role == 1) {
            // 数据权限①：试卷必须属于自己任教的课程
            teacherScope.checkCourse(request, paper.getCourseId());
            if (paper.getCreatorId() == null || !paper.getCreatorId().equals(userId)) {
                return Result.error(403, "只能操作自己创建的试卷");
            }
        }

        // 验证题目存在
        var question = questionService.getById(questionId);
        if (question == null) {
            return Result.error(404, "题目不存在");
        }
        // 数据权限②：题目也必须在自己任教的课程下（防止跨课程往试卷里塞题）
        if (role == 1) {
            teacherScope.checkCourse(request, question.getCourseId());
        }

        // 检查是否已在试卷中
        List<PaperQuestionItem> existing = paperQuestionMapper.listByPaperId(paperId);
        boolean alreadyExists = existing.stream()
                .anyMatch(pqi -> pqi.getQuestionId().equals(questionId));
        if (alreadyExists) {
            return Result.error(400, "该题目已在试卷中");
        }

        // 使用题目默认分值（若未传入）
        if (score == null || score.compareTo(BigDecimal.ZERO) <= 0) {
            score = question.getScore();
            if (score == null) {
                score = new BigDecimal("5.0");
            }
        }

        // 插入关联
        int nextOrder = paperQuestionMapper.countByPaperId(paperId) + 1;
        PaperQuestion pq = new PaperQuestion();
        pq.setPaperId(paperId);
        pq.setQuestionId(questionId);
        pq.setScore(score);
        pq.setSortOrder(nextOrder);
        paperQuestionMapper.insert(pq);

        // 更新试卷总分
        BigDecimal newTotal = paper.getTotalScore() != null
                ? paper.getTotalScore().add(score) : score;
        examPaperMapper.updateTotalScore(paperId, newTotal);

        return Result.success("已加入试卷", null);
    }
}
