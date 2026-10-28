package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.AiDifficultyResult;
import com.exam.exam_system.dto.BatchKnowledgeReq;
import com.exam.exam_system.dto.KnowledgeMatchResult;
import com.exam.exam_system.dto.QuestionKnowledgeItem;
import com.exam.exam_system.dto.WordParseResult;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.service.AiDifficultyService;
import com.exam.exam_system.service.AiKnowledgeService;
import com.exam.exam_system.service.QuestionService;
import com.exam.exam_system.service.WordParseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 题目 Controller
 * 所有接口必须先登录（被 AuthInterceptor 拦截）
 *
 * <p>接口清单：
 * <ul>
 *     <li>GET   /api/question/list         分页查询</li>
 *     <li>GET   /api/question/{id}         按 ID 查单个（编辑回显）</li>
 *     <li>POST  /api/question              新增题目</li>
 *     <li>PUT   /api/question              更新题目</li>
 *     <li>DELETE /api/question/{id}        删除题目</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/question")
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    @Autowired
    private WordParseService wordParseService;

    @Autowired
    private com.exam.exam_system.mapper.QuestionMapper questionMapper;

    @Autowired
    private com.exam.exam_system.service.FileParseService fileParseService;

    @Autowired
    private com.exam.exam_system.service.QuestionAuditService auditService;

    @Autowired
    private AiDifficultyService aiDifficultyService;

    @Autowired
    private AiKnowledgeService aiKnowledgeService;

    /** 教师数据权限：所有接口都要按「任教课程」过滤/校验，防止跨课程越权 */
    @Autowired
    private com.exam.exam_system.common.TeacherScope teacherScope;

    /**
     * 分页查询题目列表
     *
     * <p>示例：
     * <pre>
     * GET /api/question/list?pageNum=1&pageSize=10&questionType=1&difficulty=3
     * </pre>
     */
    @GetMapping("/list")
    public Result<PageResult<Question>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            // 参数名必须与前端 query 对象的 id 字段一致（params 直接展开发送）
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) Integer questionType,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy,
            HttpServletRequest request) {

        var query = new com.exam.exam_system.dto.QuestionQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setId(id);
        query.setQuestionType(questionType);
        query.setDifficulty(difficulty);
        query.setCourseId(courseId);
        query.setAuditStatus(auditStatus);
        query.setKeyword(keyword);
        // 前端切换排序时传入 "字段_方向"（如 course_asc / score_desc），为空则用默认
        query.setSortBy(sortBy != null && !sortBy.isBlank() ? sortBy : "createTime_desc");

        // 数据权限：教师只能看自己任教课程下的题目；管理员返回 null（不限制）。
        // 教师如果前端手动传了别人课程的 courseId，会因为两个条件取交集而查不到数据
        query.setCourseIds(teacherScope.myCourseIds(request));

        PageResult<Question> result = questionService.page(query);
        return Result.success("查询成功", result);
    }

    /**
     * 按 ID 查单个题目（编辑回显用）
     * GET /api/question/{id}
     */
    @GetMapping("/{id}")
    public Result<Question> getById(@PathVariable Long id, HttpServletRequest request) {
        Question q = questionService.getById(id);
        if (q == null) {
            return Result.error(404, "题目不存在");
        }
        // 数据权限：不能查看别人任教课程下的题目
        teacherScope.checkCourse(request, q.getCourseId());
        return Result.success("查询成功", q);
    }

    /**
     * 新增题目
     * POST /api/question
     * 请求体（JSON）：
     * {
     *   "courseId": 1,
     *   "questionType": 1,
     *   "content": "题干文本",
     *   "options": "[\"选项A\",\"选项B\",\"选项C\",\"选项D\"]",   // 仅单选/多选
     *   "answer": "A",
     *   "analysis": "解析",
     *   "difficulty": 2,
     *   "knowledgeId": null,
     *   "score": 2.0
     * }
     */
    @PostMapping
    @com.exam.exam_system.annotation.OperationLog(
            module = "题库管理", type = "新增",
            description = "新增#{#q.questionType}型题目：#{#q.content}")
    public Result<Long> add(@RequestBody Question q, HttpServletRequest request) {
        // 从 JWT 拦截器注入的 request 属性里取当前登录用户 ID 作为出题人
        Long userId = (Long) request.getAttribute("userId");
        q.setCreatorId(userId);
        // 新建题目默认待审核
        q.setAuditStatus(0);

        // 简单参数校验
        if (q.getContent() == null || q.getContent().isBlank()) {
            return Result.error(400, "题干不能为空");
        }
        if (q.getQuestionType() == null) {
            return Result.error(400, "题型不能为空");
        }
        // 数据权限：只能把题目建到自己任教的课程下（courseId 为空直接拒绝）
        teacherScope.checkCourseRequired(request, q.getCourseId());

        questionService.add(q);
        // 返回新题 ID（MyBatis useGeneratedKeys 已回填），供前端"撤回新增"使用
        return Result.success("新增成功", q.getId());
    }

    /**
     * 更新题目
     * PUT /api/question
     * 请求体同新增，但必须带 id
     */
    @PutMapping
    @com.exam.exam_system.annotation.OperationLog(
            module = "题库管理", type = "修改",
            description = "修改题目 id=#{#q.id}")
    public Result<Void> update(@RequestBody Question q, HttpServletRequest request) {
        if (q.getId() == null) {
            return Result.error(400, "题目ID不能为空");
        }
        Question exist = questionService.getById(q.getId());
        if (exist == null) {
            return Result.error(404, "题目不存在");
        }
        // 数据权限①：原题目必须属于自己任教的课程，否则不能改
        teacherScope.checkCourse(request, exist.getCourseId());
        // 数据权限②：不允许把题目"搬"到别人任教课程下；前端未传 courseId 时沿用原课程
        if (q.getCourseId() == null) {
            q.setCourseId(exist.getCourseId());
        }
        teacherScope.checkCourseRequired(request, q.getCourseId());

        questionService.update(q);
        return Result.success("更新成功", null);
    }

    /**
     * 删除题目
     * DELETE /api/question/{id}
     */
    @DeleteMapping("/{id}")
    @com.exam.exam_system.annotation.OperationLog(
            module = "题库管理", type = "删除",
            description = "删除题目 id=#{#id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Question exist = questionService.getById(id);
        if (exist == null) {
            return Result.error(404, "题目不存在");
        }
        // 数据权限：只能删自己任教课程下的题目
        teacherScope.checkCourse(request, exist.getCourseId());
        questionService.delete(id);
        return Result.success("删除成功", null);
    }

    /**
     * 批量删除题目
     * POST /api/question/batchDelete
     * 请求体：题目 ID 数组，如 [1, 2, 3]
     */
    @PostMapping("/batchDelete")
    @com.exam.exam_system.annotation.OperationLog(
            module = "题库管理", type = "批量删除",
            description = "批量删除#{#ids.size()}题：#{#ids}")
    public Result<Void> batchDelete(@RequestBody List<Long> ids, HttpServletRequest request) {
        if (ids == null || ids.isEmpty()) {
            return Result.error(400, "请选择要删除的题目");
        }
        // 数据权限：整批题目必须全部属于自己任教的课程（一条越权则整批拒绝）
        teacherScope.checkCourses(request, questionMapper.selectCourseIdsByIds(ids));
        questionService.deleteBatch(ids);
        return Result.success("删除成功", null);
    }

    /**
     * 批量设置题目知识点（把选中的题目统一绑定到同一组知识点，不改动审核状态）
     * POST /api/question/batchSetKnowledge
     * 请求体：{ "ids":[1,2,3], "knowledgeIds":[5,8] }（1~3 个，第一个为主知识点）
     * 兼容旧版字段：{ "ids":[1,2,3], "knowledgeId":5 }
     */
    @PostMapping("/batchSetKnowledge")
    @com.exam.exam_system.annotation.OperationLog(
            module = "题库管理", type = "批量设置知识点",
            description = "批量设置题目知识点")
    public Result<Void> batchSetKnowledge(@RequestBody BatchKnowledgeReq req, HttpServletRequest request) {
        if (req.getIds() == null || req.getIds().isEmpty()) {
            return Result.error(400, "请选择要设置知识点的题目");
        }
        List<Long> kids = req.getKnowledgeIds();
        if (kids == null || kids.isEmpty()) {
            if (req.getKnowledgeId() == null) {
                return Result.error(400, "请选择知识点");
            }
            kids = List.of(req.getKnowledgeId());
        }
        // 数据权限：只能给自己任教课程下的题目设置知识点
        teacherScope.checkCourses(request, questionMapper.selectCourseIdsByIds(req.getIds()));
        questionService.batchSetKnowledgeList(req.getIds(), kids);
        return Result.success("设置成功", null);
    }

    /**
     * 逐题保存知识点组合（AI 智能匹配"应用"：每道题绑定各自匹配到的 1~3 个知识点，不改动审核状态）
     * POST /api/question/batchSetKnowledgeItems
     * 请求体：[{ "questionId":1, "knowledgeIds":[5,8] }, ...]
     */
    @PostMapping("/batchSetKnowledgeItems")
    @com.exam.exam_system.annotation.OperationLog(
            module = "题库管理", type = "AI匹配知识点应用",
            description = "按 AI 匹配结果逐题设置知识点")
    public Result<Void> batchSetKnowledgeItems(@RequestBody List<QuestionKnowledgeItem> items,
                                               HttpServletRequest request) {
        if (items == null || items.isEmpty()) {
            return Result.error(400, "没有要保存的题目知识点");
        }
        // 数据权限：只能给自己任教课程下的题目设置知识点
        List<Long> questionIds = new ArrayList<>();
        for (QuestionKnowledgeItem item : items) {
            if (item.getQuestionId() != null) {
                questionIds.add(item.getQuestionId());
            }
        }
        if (!questionIds.isEmpty()) {
            teacherScope.checkCourses(request, questionMapper.selectCourseIdsByIds(questionIds));
        }
        questionService.batchSetKnowledgeItems(items);
        return Result.success("设置成功", null);
    }

    /**
     * AI 智能匹配知识点：根据题干/选项/答案/解析，从所属学科知识点中自动匹配最合适的一个
     * POST /api/question/aiMatchKnowledge
     * 请求体：Question 数组（需含 id、courseId、content、options、answer、analysis）
     * 返回：与输入顺序一致的 KnowledgeMatchResult 数组
     */
    @PostMapping("/aiMatchKnowledge")
    public Result<List<KnowledgeMatchResult>> aiMatchKnowledge(@RequestBody List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            return Result.error(400, "请至少提供一道题目");
        }
        if (questions.size() > 200) {
            return Result.error(400, "单次最多匹配 200 道题目，请分批处理");
        }
        try {
            List<KnowledgeMatchResult> results = aiKnowledgeService.match(questions);
            long llmCount = results.stream().filter(r -> "llm".equals(r.getSource())).count();
            String msg = llmCount > 0
                    ? "AI 匹配完成（大模型 " + llmCount + " 题，本地规则 " + (results.size() - llmCount) + " 题）"
                    : "匹配完成（本地规则引擎，配置 ai.api-key 可启用大模型更精准匹配）";
            return Result.success(msg, results);
        } catch (Exception e) {
            return Result.error(500, "AI 知识点匹配异常：" + e.getMessage());
        }
    }

    /**
     * 批量新增题目
     * POST /api/question/batch
     * 请求体：Question 数组
     * 返回：创建的题目 ID 列表
     */
    @PostMapping("/batch")
    @com.exam.exam_system.annotation.OperationLog(
            module = "题库管理", type = "批量新增",
            description = "批量新增#{#questions.size()}道题目")
    public Result<List<Long>> batchAdd(@RequestBody List<Question> questions, HttpServletRequest request) {
        if (questions == null || questions.isEmpty()) {
            return Result.error(400, "请至少提交一道题目");
        }
        Long userId = (Long) request.getAttribute("userId");
        for (Question q : questions) {
            q.setCreatorId(userId);
            q.setAuditStatus(0);  // 批量新增默认待审核
            if (q.getContent() == null || q.getContent().isBlank()) {
                return Result.error(400, "题干不能为空");
            }
            if (q.getQuestionType() == null) {
                return Result.error(400, "题型不能为空");
            }
            // 数据权限：每一道题都要建在自己任教的课程下
            teacherScope.checkCourseRequired(request, q.getCourseId());
        }
        questionService.addBatch(questions);
        // 收集创建的 ID（MyBatis useGeneratedKeys 已回填）
        List<Long> ids = new ArrayList<>();
        for (Question q : questions) {
            if (q.getId() != null) {
                ids.add(q.getId());
            }
        }
        return Result.success("批量新增成功", ids);
    }

    /**
     * 统一文件导入接口（支持 Word/Excel/PDF/JSON）
     * POST /api/question/importFile
     *
     * @param file     上传的文件（docx/xlsx/xls/pdf/json/png/jpg/jpeg）
     * @param courseId 学科 ID（可选）
     */
    @PostMapping("/importFile")
    @com.exam.exam_system.annotation.OperationLog(
            module = "题库管理", type = "导入",
            description = "导入题目文件：#{#file?.originalFilename}")
    public Result<WordParseResult> importFile(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "courseId", required = false) Long courseId,
            HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            return Result.error(400, "请上传文件");
        }
        if (file.getSize() > 20 * 1024 * 1024) {
            return Result.error(400, "文件过大（>20MB），请压缩后再上传");
        }
        try {
            Long userId = (Long) request.getAttribute("userId");
            WordParseResult result = fileParseService.parseFile(file, courseId, userId);
            if (result.getQuestions() == null || result.getQuestions().isEmpty()) {
                return Result.error(400, "未在文件中识别到有效题目，请检查文件格式");
            }
            return Result.success("解析成功，共识别 " + result.getQuestions().size() + " 题", result);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "文件解析异常：" + e.getMessage());
        }
    }

    /**
     * 统一文本/JSON 解析接口
     * POST /api/question/parseText
     *
     * 请求体：{ "type": "word"|"json", "content": "文本或JSON字符串", "courseId": 1 }
     */
    @PostMapping("/parseText")
    public Result<WordParseResult> parseText(
            @RequestBody java.util.Map<String, Object> body,
            HttpServletRequest request) {
        String type = body.get("type") != null ? body.get("type").toString().toLowerCase() : "word";
        String content = body.get("content") != null ? body.get("content").toString() : null;
        Long courseId = body.get("courseId") != null
                ? ((Number) body.get("courseId")).longValue()
                : null;

        if (content == null || content.trim().isEmpty()) {
            return Result.error(400, "请输入要解析的内容");
        }
        if (content.length() > 200000) {
            return Result.error(400, "内容过长（>20万字），请分段提交");
        }
        try {
            Long userId = (Long) request.getAttribute("userId");
            WordParseResult result;
            if ("json".equals(type)) {
                result = fileParseService.parseJson(content, courseId, userId);
            } else {
                result = wordParseService.parseText(content, courseId, userId);
            }
            if (result.getQuestions() == null || result.getQuestions().isEmpty()) {
                return Result.error(400, "未识别到有效题目，请检查格式");
            }
            return Result.success("解析成功，共识别 " + result.getQuestions().size() + " 题", result);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "解析异常：" + e.getMessage());
        }
    }

    /**
     * 从 Word 文档导入题目
     * POST /api/question/importWord
     *
     * @deprecated 建议使用 /api/question/importFile 统一接口
     * @param file     上传的 .docx 文件
     * @param courseId 学科 ID（可选）
     */
    @PostMapping("/importWord")
    public Result<WordParseResult> importWord(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "courseId", required = false) Long courseId,
            HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            return Result.error(400, "请上传 Word 文档");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".docx")) {
            return Result.error(400, "仅支持 .docx 格式文件");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            return Result.error(400, "文件过大（>10MB），请压缩后再上传");
        }

        Long userId = (Long) request.getAttribute("userId");
        WordParseResult result = wordParseService.parseWord(file, courseId, userId);

        if (result.getQuestions() == null || result.getQuestions().isEmpty()) {
            return Result.error(400, "未在文档中识别到有效题目，请检查文档格式");
        }

        return Result.success("解析成功", result);
    }

    /**
     * 从粘贴的纯文本解析题目
     * POST /api/question/importText
     *
     * @deprecated 建议使用 /api/question/parseText 统一接口
     * 请求体：{ "text": "题目文本...", "courseId": 1 }
     */
    @PostMapping("/importText")
    public Result<WordParseResult> importText(
            @RequestBody java.util.Map<String, Object> body,
            HttpServletRequest request) {
        String text = (String) body.get("text");
        Long courseId = body.get("courseId") != null
                ? ((Number) body.get("courseId")).longValue()
                : null;

        if (text == null || text.trim().isEmpty()) {
            return Result.error(400, "请输入要解析的文本内容");
        }
        if (text.length() > 100000) {
            return Result.error(400, "文本过长（>10万字），请分段提交");
        }

        Long userId = (Long) request.getAttribute("userId");
        WordParseResult result = wordParseService.parseText(text, courseId, userId);

        if (result.getQuestions() == null || result.getQuestions().isEmpty()) {
            return Result.error(400, "未在文本中识别到有效题目，请检查格式");
        }

        return Result.success("解析成功", result);
    }

    /**
     * 重新提交审核（教师修改驳回的题目后重提）
     * POST /api/question/resubmit/{id}
     */
    @PostMapping("/resubmit/{id}")
    @com.exam.exam_system.annotation.OperationLog(
            module = "题库管理", type = "重新提交审核",
            description = "重新提交题目 id=#{#id}")
    public Result<Void> resubmit(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Question q = questionService.getById(id);
        if (q == null) {
            return Result.error(404, "题目不存在");
        }
        if (q.getCreatorId() == null || !q.getCreatorId().equals(userId)) {
            return Result.error(403, "只能重新提交自己提交的题目");
        }
        // 数据权限：题目所属课程必须由自己任教，防止跨课程重新提交
        teacherScope.checkCourse(request, q.getCourseId());
        if (q.getAuditStatus() == null || q.getAuditStatus() != 2) {
            return Result.error(400, "只有驳回状态的题目才能重新提交");
        }
        auditService.resubmit(id, userId);
        return Result.success("已重新提交，等待审核", null);
    }

    // ==================== AI 难度评估接口 ====================

    /**
     * AI 批量评估题目难度
     * POST /api/question/aiEvaluateDifficulty
     *
     * <p>请求体：Question 数组（前端当前批量列表的题目）。字段要求：
     * questionType、content、options、answer、analysis、courseName（可选）
     *
     * <p>返回值：与输入顺序一致的 AiDifficultyResult 数组。
     * 每项包含 difficulty(1~3)、difficultyText(简单/中等/困难)、source(llm/local)、reason
     *
     * <p>说明：
     * - 若配置了 ai.api-key：调用大模型综合评估，结果更准确；
     * - 未配置或调用失败：自动降级为本地规则启发式评估，保证离线可用。
     */
    @PostMapping("/aiEvaluateDifficulty")
    public Result<List<AiDifficultyResult>> aiEvaluateDifficulty(
            @RequestBody List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            return Result.error(400, "请至少提供一道题目进行评估");
        }
        if (questions.size() > 200) {
            return Result.error(400, "单次最多评估 200 道题目，请分批处理");
        }
        try {
            List<AiDifficultyResult> results = aiDifficultyService.evaluateBatch(questions);
            long llmCount = results.stream().filter(r -> "llm".equals(r.getSource())).count();
            String msg = llmCount > 0
                    ? "评估完成（大模型 " + llmCount + " 题，本地规则 " + (results.size() - llmCount) + " 题）"
                    : "评估完成（本地规则引擎，配置 ai.api-key 可启用大模型更精准评估）";
            return Result.success(msg, results);
        } catch (Exception e) {
            return Result.error(500, "AI 难度评估异常：" + e.getMessage());
        }
    }

    /**
     * AI 评估单道题难度（供单题编辑页使用）
     * POST /api/question/aiEvaluateDifficultyOne
     */
    @PostMapping("/aiEvaluateDifficultyOne")
    public Result<AiDifficultyResult> aiEvaluateDifficultyOne(@RequestBody Question question) {
        if (question == null) {
            return Result.error(400, "题目数据不能为空");
        }
        try {
            AiDifficultyResult r = aiDifficultyService.evaluate(question);
            return Result.success(
                    "llm".equals(r.getSource()) ? "大模型评估完成" : "本地规则评估完成",
                    r);
        } catch (Exception e) {
            return Result.error(500, "AI 难度评估异常：" + e.getMessage());
        }
    }

    // ==================== 导入模板下载接口 ====================

    /**
     * 下载 Excel 导入模板（.xlsx）
     * GET /api/question/template/excel
     *
     * 表头：题干、题型、A、B、C、D、E、F、答案、解析、分值
     */
    @GetMapping("/template/excel")
    public void downloadExcelTemplate(HttpServletResponse response) throws IOException {
        String filename = "Excel文件题目模板_" + timestamp() + ".xlsx";
        writeExcelTemplate(response, filename);
    }

    /**
     * 下载 JSON 导入模板（示例数组）
     * GET /api/question/template/json
     */
    @GetMapping("/template/json")
    public void downloadJsonTemplate(HttpServletResponse response) throws IOException {
        String filename = "JSON题目模板_" + timestamp() + ".json";
        writeJsonTemplate(response, filename);
    }

    /**
     * 下载 TXT 文本导入模板（格式样例）
     * GET /api/question/template/txt
     */
    @GetMapping("/template/txt")
    public void downloadTxtTemplate(HttpServletResponse response) throws IOException {
        String filename = "TXT文本题目模板_" + timestamp() + ".txt";
        writeTxtTemplate(response, filename);
    }

    /**
     * 下载 Word 文件导入模板（格式样例参考）
     * GET /api/question/template/word
     *
     * 说明：Word 解析属于弱解析，支持数字编号+A-F选项+内联答案的标准格式；
     * 含表格、图片、复杂分栏的 Word 文档解析失败率较高，正式批量录入建议使用 Excel 模板。
     */
    @GetMapping("/template/word")
    public void downloadWordTemplate(HttpServletResponse response) throws IOException {
        String filename = "Word文件题目模板_" + timestamp() + ".docx";
        writeWordTemplate(response, filename);
    }

    // ============ 模板生成辅助方法 ============

    private String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    private void setFileResponse(HttpServletResponse response, String filename, String contentType) throws IOException {
        String encodedName = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);
    }

    private void writeExcelTemplate(HttpServletResponse response, String filename) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("题目导入模板");

            // 表头行
            Row headerRow = sheet.createRow(0);
            String[] headers = {"题干", "题型", "A", "B", "C", "D", "E", "F", "答案", "解析", "分值"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // 示例数据行（3 道题）
            String[][] examples = {
                    {"Java 中用于声明常量的关键字是？", "1（单选）", "const", "final", "static", "volatile", "", "", "B", "final 关键字用于声明常量", "5"},
                    {"以下哪些是 Java 的访问修饰符？", "2（多选）", "public", "private", "protected", "internal", "", "", "ABC", "public/private/protected 是 Java 三大访问修饰符", "5"},
                    {"Java 是一种____语言。", "4（填空）", "", "", "", "", "", "", "面向对象", "Java 是面向对象编程语言", "3"}
            };

            for (int r = 0; r < examples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < examples[r].length; c++) {
                    row.createCell(c).setCellValue(examples[r][c]);
                }
            }

            // 自动调整列宽（简化版：设固定宽度）
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, (short) 15000);
            }

            setFileResponse(response, filename, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            try (OutputStream os = response.getOutputStream()) {
                workbook.write(os);
            }
        }
    }

    private void writeJsonTemplate(HttpServletResponse response, String filename) throws IOException {
        List<Map<String, Object>> template = new ArrayList<>();

        // 示例：单选题
        Map<String, Object> q1 = new LinkedHashMap<>();
        q1.put("content", "Java 中用于声明常量的关键字是？");
        q1.put("questionType", 1);
        q1.put("options", List.of("const", "final", "static", "volatile"));
        q1.put("answer", "B");
        q1.put("analysis", "final 关键字用于声明常量");
        q1.put("difficulty", 2);
        q1.put("score", 5);
        template.add(q1);

        // 示例：多选题
        Map<String, Object> q2 = new LinkedHashMap<>();
        q2.put("content", "以下哪些是 Java 的访问修饰符？");
        q2.put("questionType", 2);
        q2.put("options", List.of("public", "private", "protected", "internal"));
        q2.put("answer", "ABC");
        q2.put("analysis", "public/private/protected 是 Java 三大访问修饰符");
        q2.put("difficulty", 1);
        q2.put("score", 5);
        template.add(q2);

        // 示例：填空题
        Map<String, Object> q3 = new LinkedHashMap<>();
        q3.put("content", "Java 是一种____语言。");
        q3.put("questionType", 4);
        q3.put("answer", "面向对象");
        q3.put("analysis", "Java 是面向对象编程语言");
        q3.put("difficulty", 1);
        q3.put("score", 3);
        template.add(q3);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(template);

        setFileResponse(response, filename, "application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json);
    }

    private void writeTxtTemplate(HttpServletResponse response, String filename) throws IOException {
        String sample = """
                题目导入模板 — TXT 格式样例
                （支持数字编号、A-F 选项、(X) 内联答案；也支持「题目：/答案：/解析：」格式）

                1. Java 中用于声明常量的关键字是？(B)
                   A. const
                   B. final
                   C. static
                   D. volatile
                解析：final 关键字用于声明常量。

                2. 以下哪些是 Java 的访问修饰符？(ABC)
                   A. public
                   B. private
                   C. protected
                   D. internal
                解析：public/private/protected 是 Java 三大访问修饰符。

                3. Java 是一种____语言。
                   答案：面向对象
                解析：Java 是面向对象编程语言。

                4. 题目：简述 Java 中接口和抽象类的区别。
                   答案：
                   ① 接口只能包含常量和抽象方法（Java 8+ 可含 default 方法）；
                   ② 抽象类可以包含抽象方法和具体方法；
                   ③ 一个类可以实现多个接口，但只能继承一个抽象类。
                解析：接口和抽象类是 Java 实现多态和复用的重要机制。
                """;

        setFileResponse(response, filename, "text/plain; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(sample);
    }

    private void writeWordTemplate(HttpServletResponse response, String filename) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            // 标题（加大加粗）
            XWPFParagraph title = doc.createParagraph();
            title.setStyle("Heading1");
            XWPFRun titleRun = title.createRun();
            titleRun.setText("Word 文件题目模板（格式样例参考）");
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.addBreak();

            // 说明
            XWPFParagraph intro = doc.createParagraph();
            XWPFRun introRun = intro.createRun();
            introRun.setText("【使用说明】请按以下格式录入题目，每道题用数字开头，答案用括号标注在题干末尾；系统可自动识别单选 / 多选 / 判断 / 填空 / 简答。Word 解析属于弱解析，含表格、图片、复杂分栏的文档建议转 Excel 模板后再导入。");
            introRun.setFontSize(10);
            introRun.setColor("666666");
            introRun.addBreak();
            introRun.addBreak();

            // 题 1：单选
            addWordParagraph(doc, "1. Java 中用于声明常量的关键字是？(B)", true, false);
            addWordParagraph(doc, "   A. const", false, false);
            addWordParagraph(doc, "   B. final", false, false);
            addWordParagraph(doc, "   C. static", false, false);
            addWordParagraph(doc, "   D. volatile", false, false);
            addWordParagraph(doc, "解析：final 关键字用于声明常量。", false, true);

            // 题 2：多选
            addWordParagraph(doc, "2. 以下哪些是 Java 的访问修饰符？(ABC)", true, false);
            addWordParagraph(doc, "   A. public", false, false);
            addWordParagraph(doc, "   B. private", false, false);
            addWordParagraph(doc, "   C. protected", false, false);
            addWordParagraph(doc, "   D. internal", false, false);
            addWordParagraph(doc, "解析：public/private/protected 是 Java 三大访问修饰符。", false, true);

            // 题 3：填空
            addWordParagraph(doc, "3. Java 是一种____语言。", true, false);
            addWordParagraph(doc, "答案：面向对象", false, false);
            addWordParagraph(doc, "解析：Java 是面向对象编程语言。", false, true);

            // 题 4：简答
            addWordParagraph(doc, "4. 题目：简述 Java 中接口和抽象类的区别。", true, false);
            addWordParagraph(doc, "答案：", false, false);
            addWordParagraph(doc, "① 接口只能包含常量和抽象方法（Java 8+ 可含 default 方法）；", false, false);
            addWordParagraph(doc, "② 抽象类可以包含抽象方法和具体方法；", false, false);
            addWordParagraph(doc, "③ 一个类可以实现多个接口，但只能继承一个抽象类。", false, false);
            addWordParagraph(doc, "解析：接口和抽象类是 Java 实现多态和复用的重要机制。", false, true);

            setFileResponse(response, filename, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            try (OutputStream os = response.getOutputStream()) {
                doc.write(os);
            }
        }
    }

    /** 辅助：向 Word 文档追加一段文本 */
    private static void addWordParagraph(XWPFDocument doc, String text, boolean bold, boolean isAnalysis) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(bold);
        if (isAnalysis) {
            run.setColor("999999");
            run.setFontSize(10);
        }
    }
}
