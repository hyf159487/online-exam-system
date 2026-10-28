package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.AiComposeMeta;
import com.exam.exam_system.dto.AiComposePlan;
import com.exam.exam_system.dto.AiComposeRequest;
import com.exam.exam_system.dto.PaperDetail;
import com.exam.exam_system.dto.PaperQuestionSaveRequest;
import com.exam.exam_system.dto.RandomComposeRequest;
import com.exam.exam_system.entity.ExamPaper;
import com.exam.exam_system.service.AiComposeService;
import com.exam.exam_system.service.ExamPaperService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 试卷 Controller
 * 所有接口必须先登录（被 AuthInterceptor 拦截）
 *
 * <p>接口清单：
 * <ul>
 *     <li>GET    /api/exam-paper/list              分页查询（名称模糊/状态筛选）</li>
 *     <li>GET    /api/exam-paper/{id}              试卷详情（基本信息+题目列表）</li>
 *     <li>POST   /api/exam-paper                   新增试卷（草稿）</li>
 *     <li>PUT    /api/exam-paper                   更新基本信息</li>
 *     <li>DELETE /api/exam-paper/{id}              删除试卷（连同题目关联）</li>
 *     <li>PUT    /api/exam-paper/{id}/publish      发布试卷</li>
 *     <li>POST   /api/exam-paper/questions         保存组卷结果</li>
 *     <li>POST   /api/exam-paper/{id}/random-compose 随机组卷（规则抽题）</li>
 *     <li>GET    /api/exam-paper/{id}/ai-compose/meta AI 智能组卷资源概览</li>
 *     <li>POST   /api/exam-paper/{id}/ai-compose    AI 智能组卷（方案 + 自动抽题）</li>
 *     <li>GET    /api/exam-paper/recent            最近 N 张（首页仪表盘用）</li>
 *     <li>GET    /api/exam-paper/mine              我创建的试卷</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/exam-paper")
public class ExamPaperController {

    @Autowired
    private ExamPaperService examPaperService;

    /** 教师数据权限：试卷按「任教课程」隔离，防止跨课程查看/修改 */
    @Autowired
    private com.exam.exam_system.common.TeacherScope teacherScope;

    /** AI 智能组卷：生成组卷方案并自动抽题 */
    @Autowired
    private AiComposeService aiComposeService;

    /**
     * 最近 N 张试卷
     * GET /api/exam-paper/recent?limit=10
     */
    @GetMapping("/recent")
    public Result<List<ExamPaper>> recent(@RequestParam(defaultValue = "10") int limit,
                                          HttpServletRequest request) {
        // 数据权限：教师只返回自己任教课程下的试卷（管理员返回 null = 不限制）
        return Result.success("查询成功",
                examPaperService.listRecent(limit, teacherScope.myCourseIds(request)));
    }

    /**
     * 当前登录教师创建的试卷
     * GET /api/exam-paper/mine
     */
    @GetMapping("/mine")
    public Result<List<ExamPaper>> mine(HttpServletRequest request) {
        Long creatorId = (Long) request.getAttribute("userId"); // 拦截器放进去的
        return Result.success("查询成功", examPaperService.listByCreator(creatorId));
    }

    /**
     * 分页查询试卷列表
     * GET /api/exam-paper/list?pageNum=1&pageSize=10&paperName=期中&status=0&courseId=1
     *
     * <p>权限隔离：管理员看全部；教师只能看「自己任教课程下」的试卷，
     * 且在该范围内只看自己创建的 + 被共享给自己的。
     */
    @GetMapping("/list")
    public Result<PageResult<ExamPaper>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String paperName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long courseId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");
        // 数据权限：教师任教课程集合（null = 管理员，不限制）
        List<Long> courseIds = teacherScope.myCourseIds(request);
        return Result.success("查询成功",
                examPaperService.page(pageNum, pageSize, paperName, status, courseId, userId, role, courseIds));
    }

    /**
     * 试卷详情（组卷页回显：基本信息 + 已选题目）
     * GET /api/exam-paper/{id}
     */
    @GetMapping("/{id}")
    public Result<PaperDetail> detail(@PathVariable Long id, HttpServletRequest request) {
        PaperDetail detail = examPaperService.detail(id);
        if (detail == null) {
            return Result.error(404, "试卷不存在");
        }
        // 数据权限：不能查看别人任教课程下的试卷
        teacherScope.checkCourse(request, detail.getPaper().getCourseId());
        return Result.success("查询成功", detail);
    }

    /**
     * 新增试卷（默认草稿状态）
     * POST /api/exam-paper
     * 请求体（JSON）：
     * {
     *   "paperName": "Java基础期中卷",
     *   "courseId": 1,
     *   "paperType": 1,          // 1手动 2随机 3AI智能
     *   "duration": 60
     *   // 及格分不用传：后端按 满分 × 60% 自动换算，组卷保存后会再次同步
     * }
     */
    @PostMapping
    public Result<Long> add(@RequestBody ExamPaper paper, HttpServletRequest request) {
        if (paper.getPaperName() == null || paper.getPaperName().isBlank()) {
            return Result.error(400, "试卷名称不能为空");
        }
        // 当前登录教师作为创建人
        Long userId = (Long) request.getAttribute("userId");
        paper.setCreatorId(userId);
        // 数据权限：只能把试卷建到自己任教的课程下（courseId 为空直接拒绝，服务层也会兜底）
        teacherScope.checkCourseRequired(request, paper.getCourseId());

        examPaperService.add(paper);
        // paper.id 已被 MyBatis 回填，返回给前端（前端拿到 id 直接跳组卷页）
        return Result.success("新增成功", paper.getId());
    }

    /**
     * 更新试卷基本信息
     * PUT /api/exam-paper
     * 请求体同新增，必须带 id
     */
    @PutMapping
    public Result<Void> update(@RequestBody ExamPaper paper, HttpServletRequest request) {
        if (paper.getId() == null) {
            return Result.error(400, "试卷ID不能为空");
        }
        checkWritable(paper.getId(), request);
        examPaperService.update(paper);
        return Result.success("更新成功", null);
    }

    /**
     * 删除试卷（连同题目关联一起删）
     * DELETE /api/exam-paper/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        checkWritable(id, request);
        examPaperService.delete(id);
        return Result.success("删除成功", null);
    }

    /**
     * 发布试卷（草稿 → 已发布）
     * PUT /api/exam-paper/{id}/publish
     */
    @PutMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id, HttpServletRequest request) {
        checkWritable(id, request);
        examPaperService.publish(id);
        return Result.success("发布成功", null);
    }

    /**
     * 撤回试卷（已发布 → 草稿，仅已发布状态可撤回）
     * PUT /api/exam-paper/{id}/revoke
     */
    @PutMapping("/{id}/revoke")
    public Result<Void> revoke(@PathVariable Long id, HttpServletRequest request) {
        checkWritable(id, request);
        examPaperService.revoke(id);
        return Result.success("撤回成功", null);
    }

    /**
     * 归档试卷（已发布 → 已归档；归档后不可改/不可组卷/不可用于新建考试）
     * PUT /api/exam-paper/{id}/archive
     */
    @PutMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id, HttpServletRequest request) {
        checkWritable(id, request);
        examPaperService.archive(id);
        return Result.success("归档成功", null);
    }

    /**
     * 批量归档试卷（仅已发布状态可被归档）
     * PUT /api/exam-paper/batch-archive?ids=1,2,3
     */
    @PutMapping("/batch-archive")
    public Result<Integer> archiveBatch(@RequestParam("ids") List<Long> ids, HttpServletRequest request) {
        if (ids == null || ids.isEmpty()) {
            return Result.error(400, "请选择要归档的试卷");
        }
        for (Long id : ids) {
            checkWritable(id, request);
        }
        int n = examPaperService.archiveBatch(ids);
        return Result.success("归档完成，共归档 " + n + " 张试卷", n);
    }

    /**
     * 保存组卷结果（全量覆盖式：先清空旧关联，再插入新关联，自动算总分）
     * POST /api/exam-paper/questions
     * 请求体（JSON）：
     * {
     *   "paperId": 1,
     *   "items": [
     *     {"questionId": 10, "score": 2.0},
     *     {"questionId": 11, "score": 5.0}
     *   ]
     * }
     */
    @PostMapping("/questions")
    public Result<Void> saveQuestions(@RequestBody PaperQuestionSaveRequest request, HttpServletRequest httpRequest) {
        if (request.getPaperId() == null) {
            return Result.error(400, "试卷ID不能为空");
        }
        checkWritable(request.getPaperId(), httpRequest);
        examPaperService.saveQuestions(request);
        return Result.success("组卷保存成功", null);
    }

    /**
     * 随机组卷：按题型/难度/数量规则从题库随机抽题，覆盖式生成试卷题目
     * POST /api/exam-paper/{id}/random-compose
     * 请求体（JSON）：
     * {
     *   "rules": [
     *     {"questionType": 1, "difficulty": 2, "count": 10, "score": 2.0},
     *     {"questionType": 5, "difficulty": null, "count": 2, "score": 10.0}
     *   ]
     * }
     */
    @PostMapping("/{id}/random-compose")
    public Result<Integer> randomCompose(@PathVariable Long id,
                                         @RequestBody RandomComposeRequest request,
                                         HttpServletRequest httpRequest) {
        checkWritable(id, httpRequest);
        if (request.getRules() == null || request.getRules().isEmpty()) {
            return Result.error(400, "请至少配置一条抽题规则");
        }
        int total = examPaperService.randomCompose(id, request.getRules());
        return Result.success("随机组卷成功，共抽取 " + total + " 题", total);
    }

    /**
     * AI 智能组卷：组卷资源概览（题库可用题量 / 知识点题量 / 大模型启用状态）
     * GET /api/exam-paper/{id}/ai-compose/meta
     */
    @GetMapping("/{id}/ai-compose/meta")
    public Result<AiComposeMeta> aiComposeMeta(@PathVariable Long id, HttpServletRequest request) {
        checkWritable(id, request);
        return Result.success("查询成功", aiComposeService.meta(id));
    }

    /**
     * AI 智能组卷：按「目标总分 + 难度倾向 + 重点知识点 + 一句话要求」生成组卷方案并自动抽题（覆盖式）
     * POST /api/exam-paper/{id}/ai-compose
     * 请求体（JSON，全部可空）：
     * {
     *   "totalScore": 100,
     *   "difficultyPreference": 2,        // 1偏基础 2均衡 3偏挑战
     *   "knowledgeIds": [3, 5],
     *   "requirement": "侧重第三章，计算与综合应用题多一点"
     * }
     */
    @PostMapping("/{id}/ai-compose")
    public Result<AiComposePlan> aiCompose(@PathVariable Long id,
                                           @RequestBody(required = false) AiComposeRequest request,
                                           HttpServletRequest httpRequest) {
        checkWritable(id, httpRequest);
        AiComposePlan plan = aiComposeService.compose(id, request);
        String sourceText = "llm".equals(plan.getSource()) ? "AI 大模型" : "本地规则引擎";
        return Result.success("智能组卷完成（" + sourceText + "）：共 " + plan.getQuestionCount() + " 题 / "
                + plan.getTotalScore().stripTrailingZeros().toPlainString() + " 分", plan);
    }

    /**
     * 批量删除试卷（连同题目关联一起删除，事务）
     * DELETE /api/exam-paper/batch?ids=1,2,3
     */
    @DeleteMapping("/batch")
    public Result<Integer> deleteBatch(@RequestParam("ids") List<Long> ids, HttpServletRequest request) {
        if (ids == null || ids.isEmpty()) {
            return Result.error(400, "请选择要删除的试卷");
        }
        if (ids.size() > 200) {
            return Result.error(400, "单次最多删除 200 张试卷");
        }
        for (Long id : ids) {
            checkWritable(id, request);
        }
        int n = examPaperService.deleteBatch(ids);
        return Result.success("删除完成，共删除 " + n + " 张试卷", n);
    }

    /**
     * 写权限保护：共享可见 ≠ 可修改。仅创建人和管理员可编辑/删除/发布/组卷。
     * 越权时抛异常（由全局异常处理器统一返回）
     *
     * <p>两道校验：
     * <ol>
     *     <li>课程维度（{@link com.exam.exam_system.common.TeacherScope}）：
     *         试卷必须落在当前教师任教的课程上，防止跨课程操作；</li>
     *     <li>创建人维度：同一课程下别人创建并共享给我的试卷，仅可查看。</li>
     * </ol>
     */
    private void checkWritable(Long paperId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Integer role = (Integer) request.getAttribute("role");
        if (role != null && role == 2) {
            return; // 管理员
        }
        com.exam.exam_system.entity.ExamPaper paper = examPaperService.getById(paperId);
        if (paper == null) {
            throw new com.exam.exam_system.common.BizException("试卷不存在（id=" + paperId + "）");
        }
        // 数据权限：试卷所属课程必须由当前教师任教
        teacherScope.checkCourse(request, paper.getCourseId());
        if (paper.getCreatorId() == null || !paper.getCreatorId().equals(userId)) {
            throw new com.exam.exam_system.common.BizException("只能操作自己创建的试卷（该试卷由他人创建并共享给您，仅可查看）");
        }
    }

}
