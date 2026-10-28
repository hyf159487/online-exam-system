package com.exam.exam_system.controller;

import com.exam.exam_system.common.BizException;
import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.AiChatRequest;
import com.exam.exam_system.dto.AiExplainResult;
import com.exam.exam_system.dto.CoursePracticeStats;
import com.exam.exam_system.dto.PracticeConfig;
import com.exam.exam_system.dto.QuestionPracticeStat;
import com.exam.exam_system.dto.PracticeQuestionQuery;
import com.exam.exam_system.dto.PracticeSubmitRequest;
import com.exam.exam_system.dto.PracticeSubmitResult;
import com.exam.exam_system.entity.KnowledgePoint;
import com.exam.exam_system.entity.PracticeAnswer;
import com.exam.exam_system.entity.PracticePaper;
import com.exam.exam_system.entity.PracticePaperQuestion;
import com.exam.exam_system.entity.PracticeRecord;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.entity.StudentPracticeLog;
import com.exam.exam_system.entity.WrongQuestion;
import com.exam.exam_system.mapper.CourseMemberMapper;
import com.exam.exam_system.mapper.PracticeAnswerMapper;
import com.exam.exam_system.mapper.PracticePaperMapper;
import com.exam.exam_system.mapper.PracticePaperQuestionMapper;
import com.exam.exam_system.mapper.PracticeRecordMapper;
import com.exam.exam_system.mapper.StudentPracticeLogMapper;
import com.exam.exam_system.service.AiExplainService;
import com.exam.exam_system.service.KnowledgePointService;
import com.exam.exam_system.service.PracticeService;
import com.exam.exam_system.service.WrongQuestionService;
import com.exam.exam_system.util.AnswerJudge;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 学生端「自主练习」Controller
 * 路径前缀 /api/student/practice，token 校验 + userId 注入由 AuthInterceptor 统一处理。
 *
 * <p>两大能力：
 * <ol>
 *   <li>按学科自由刷题（按学科拉随机题 + 单题提交判分）</li>
 *   <li>做教师/管理员发布的练习卷（列表 → 详情 → 整套提交 → 写练习记录 + 错题归集）</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/student/practice")
public class PracticeController {

    @Autowired
    private PracticeService practiceService;

    @Autowired
    private KnowledgePointService knowledgePointService;

    @Autowired
    private AiExplainService aiExplainService;

    @Autowired
    private PracticePaperMapper practicePaperMapper;

    @Autowired
    private PracticePaperQuestionMapper ppqMapper;

    @Autowired
    private PracticeRecordMapper recordMapper;

    @Autowired
    private StudentPracticeLogMapper practiceLogMapper;

    @Autowired
    private PracticeAnswerMapper answerMapper;

    @Autowired
    private WrongQuestionService wrongQuestionService;

    @Autowired
    private CourseMemberMapper courseMemberMapper;

    // ========== 一、按学科自由刷题 ==========

    /**
     * 随机抽取练习题目（含答案与解析，做题过程中可随时在「答题 / 背题」间切换）
     * GET /api/student/practice/questions?courseId=1&questionTypes=1,2&difficulties=1,2&knowledgeId=3&count=10
     * <ul>
     *   <li>courseId      学科（题库来源必选；错题/收藏来源可空=不限学科）</li>
     *   <li>questionTypes 题型多选（1/2/3/4/5 逗号分隔，空=全部）</li>
     *   <li>difficulties  难度多选（1~5 逗号分隔，空=全部，仅题库来源生效）</li>
     *   <li>knowledgeId   知识点（可选，仅题库来源生效）</li>
     *   <li>count         随机抽取题量（题库 5~50；错题/收藏 1~50，默认 10）</li>
     *   <li>source        出题来源：空/normal=题库随机，wrong=我的错题，favorite=我的收藏</li>
     *   <li>wrongCountMin / wrongCountMax 错误次数区间（仅 source=wrong 生效）</li>
     * </ul>
     *
     * <p>「我的错题 / 我的收藏」页的批量练习入口即通过 source 复用本接口抽题。</p>
     */
    @GetMapping("/questions")
    public Result<List<Question>> questions(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) String questionTypes,
            @RequestParam(required = false) String difficulties,
            @RequestParam(required = false) Long knowledgeId,
            @RequestParam(defaultValue = "10") Integer count,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer wrongCountMin,
            @RequestParam(required = false) Integer wrongCountMax,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        PracticeQuestionQuery query = new PracticeQuestionQuery();
        query.setCourseId(courseId);
        query.setQuestionTypes(parseCsvIds(questionTypes));
        query.setDifficulties(parseCsvIds(difficulties));
        query.setKnowledgeId(knowledgeId);
        query.setCount(count);
        query.setSource(source);
        query.setWrongCountMin(wrongCountMin);
        query.setWrongCountMax(wrongCountMax);
        return Result.success("查询成功", practiceService.getPracticeQuestions(userId, query));
    }

    /**
     * 背题模式 AI 智能讲解（首轮）：对当前题目生成个性化讲解
     * POST /api/student/practice/questions/{id}/explain
     */
    @PostMapping("/questions/{id}/explain")
    public Result<AiExplainResult> explainQuestion(@PathVariable Long id) {
        return Result.success("生成成功", aiExplainService.explainQuestion(id, null));
    }

    /** 背题模式 AI 多轮对话：携带历史上下文继续追问 */
    @PostMapping("/questions/{id}/chat")
    public Result<AiExplainResult> chatQuestion(@PathVariable Long id,
                                                @RequestBody AiChatRequest req) {
        if (req == null || req.getQuestion() == null || req.getQuestion().isBlank()) {
            return Result.error(400, "请输入你的问题");
        }
        return Result.success("ok", aiExplainService.chatQuestion(id, null, req.getQuestion(), req.getHistory()));
    }

    /**
     * 学生端：按课程查知识点（刷题条件「知识点下拉」用，只读）
     * GET /api/student/practice/knowledge-points?courseId=xx
     */
    @GetMapping("/knowledge-points")
    public Result<List<KnowledgePoint>> knowledgePoints(@RequestParam(required = false) Long courseId,
                                                        HttpServletRequest request) {
        if (courseId == null) {
            return Result.error(400, "课程ID不能为空");
        }
        Long userId = (Long) request.getAttribute("userId");
        if (!isJoinedCourse(userId, courseId)) {
            return Result.error(403, "只能查看已加入课程的知识点");
        }
        return Result.success("查询成功", knowledgePointService.listByCourse(courseId, null));
    }

    /**
     * 某学科练习统计：题库总题数 + 该学生已作答过的去重题数（选中学科后展示）
     * GET /api/student/practice/stat?courseId=xx
     */
    @GetMapping("/stat")
    public Result<CoursePracticeStats> stat(@RequestParam(required = false) Long courseId,
                                            HttpServletRequest request) {
        if (courseId == null) {
            return Result.error(400, "课程ID不能为空");
        }
        Long userId = (Long) request.getAttribute("userId");
        if (!isJoinedCourse(userId, courseId)) {
            return Result.error(403, "只能统计已加入课程的练习情况");
        }
        return Result.success("查询成功", practiceService.courseStats(userId, courseId));
    }

    /** 校验该课程是否属于当前学生已加入的课程 */
    private boolean isJoinedCourse(Long studentId, Long courseId) {
        if (studentId == null || courseId == null) {
            return false;
        }
        List<Long> joined = courseMemberMapper.selectJoinedCourseIds(studentId);
        return joined != null && joined.contains(courseId);
    }

    /**
     * 某道题的练习统计：该学生总作答次数 + 答对次数（答题展示「我已做过几次，正确率多少」）
     * GET /api/student/practice/question-stat?questionId=xx
     */
    @GetMapping("/question-stat")
    public Result<QuestionPracticeStat> questionStat(@RequestParam(required = false) Long questionId,
                                                     HttpServletRequest request) {
        if (questionId == null) {
            return Result.error(400, "题目ID不能为空");
        }
        Long userId = (Long) request.getAttribute("userId");
        return Result.success("查询成功", practiceService.questionStat(userId, questionId));
    }

    /** 把逗号分隔的数字字符串解析为 List<Integer>（空串返回空列表） */
    private List<Integer> parseCsvIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::valueOf)
                .collect(Collectors.toList());
    }

    /** 提交单题作答：判分 + 答错归集错题本 */
    @PostMapping("/submit")
    public Result<PracticeSubmitResult> submit(@RequestBody PracticeSubmitRequest req, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (req.getQuestionId() == null) {
            return Result.error(400, "题目ID不能为空");
        }
        return Result.success("提交成功", practiceService.submit(userId, req));
    }

    /** 读取错题自动移除策略 */
    @GetMapping("/config")
    public Result<PracticeConfig> getConfig(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success("查询成功", practiceService.getConfig(userId));
    }

    /** 保存错题自动移除策略 */
    @PutMapping("/config")
    public Result<PracticeConfig> saveConfig(@RequestBody PracticeConfig cfg, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success("保存成功", practiceService.saveConfig(userId, cfg));
    }

    // ========== 二、教师发布的练习卷 ==========

    /**
     * 学生端：列出所有【已发布】的练习卷（按学科可选筛选，最多 200 条，按创建时间倒序）
     * GET /api/student/practice/papers?courseId=xxx
     */
    @GetMapping("/papers")
    public Result<PageResult<PracticePaper>> listPublishedPapers(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long courseId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        // 只统计已发布且属于"我已加入课程"的练习卷数量
        long total = practicePaperMapper.countPage(null, null, null, courseId, 1, userId);
        List<PracticePaper> list = practicePaperMapper.listPage(
                (pageNum - 1) * pageSize, pageSize,
                null,   // teacherId 留空 → 不过滤创建人
                null,   // role 留空 → 不过滤角色
                null,   // keyword 留空
                courseId,
                1,      // status=1 → 只拿已发布的
                userId); // studentId → 统计当前学生自己的练习次数
        // 裁掉内部字段，前端不需要 hasPracticeRecords
        list.forEach(p -> p.setHasPracticeRecords(null));
        // 文件型作业：回填「我的提交」（提交状态 / 附件 / 教师评分评语）
        fillMySubmit(list);
        return Result.success("查询成功",
                PageResult.of(list, total, pageNum.longValue(), pageSize.longValue()));
    }

    /**
     * 学生端：取某张已发布练习卷的题目（**不含正确答案和解析**，防作弊）
     * GET /api/student/practice/papers/{id}
     */
    @GetMapping("/papers/{id}")
    public Result<List<PracticePaperQuestion>> paperDetail(@PathVariable Long id) {
        PracticePaper p = practicePaperMapper.selectById(id);
        if (p == null || (p.getIsDeleted() != null && p.getIsDeleted() == 1)) {
            return Result.error(404, "练习卷不存在或已删除");
        }
        if (p.getStatus() == null || p.getStatus() != 1) {
            return Result.error(400, "该练习卷尚未发布，暂不可做");
        }
        List<PracticePaperQuestion> rows = ppqMapper.listByPaperId(id);
        if (rows == null) rows = List.of();
        // 去掉正确答案和解析，防学生直接刷详情拿答案
        for (PracticePaperQuestion q : rows) {
            q.setCorrectAnswer(null);
            q.setAnalysis(null);
        }
        return Result.success("查询成功", rows);
    }

    /**
     * 学生端：整套提交某张练习卷
     * POST /api/student/practice/papers/{id}/submit
     * body: { answers: [ { questionId, userAnswer } ] }
     *
     * @return 记录ID + 得分 + 明细（每题对错 + 正确答案 + 解析）+ 是否新归集错题
     */
    @PostMapping("/papers/{id}/submit")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> submitPaper(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String userName = (String) request.getAttribute("userName");
        String realName = (String) request.getAttribute("realName");
        String displayName = (realName != null && !realName.isBlank()) ? realName : userName;

        PracticePaper paper = practicePaperMapper.selectById(id);
        if (paper == null || (paper.getIsDeleted() != null && paper.getIsDeleted() == 1)) {
            throw new BizException("练习卷不存在或已删除");
        }
        if (paper.getStatus() == null || paper.getStatus() != 1) {
            throw new BizException("该练习卷尚未发布，暂不可做");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> answersRaw = (List<Map<String, Object>>) body.get("answers");
        if (answersRaw == null || answersRaw.isEmpty()) {
            throw new BizException("未提交任何答案");
        }
        // 把前端答案按 questionId 索引，便于 O(1) 查
        Map<Long, String> userAnswerMap = new HashMap<>();
        for (Map<String, Object> a : answersRaw) {
            Object qid = a.get("questionId");
            Object ua = a.get("userAnswer");
            if (qid != null && ua != null) {
                userAnswerMap.put(Long.valueOf(qid.toString()), ua.toString());
            }
        }

        // 取练习卷题目（带 correctAnswer 和 analysis 用于判分 + 明细）
        List<PracticePaperQuestion> questions = ppqMapper.listByPaperId(id);
        if (questions == null || questions.isEmpty()) {
            throw new BizException("该练习卷还没有题目");
        }

        // 判分 + 写 practice_record + practice_answer + 错题归集
        int correctCount = 0;
        int wrongCount = 0;
        int scoreSum = 0;
        int newlyCollected = 0;
        List<Map<String, Object>> detailList = new ArrayList<>();
        List<PracticeAnswer> answerRows = new ArrayList<>();

        int subjectiveCount = 0; // 主观题题数，>0 时该次作业进入教师端「批改作业」待批改列表
        for (PracticePaperQuestion q : questions) {
            String userAnswer = userAnswerMap.getOrDefault(q.getQuestionId(), "");
            String correctAnswer = q.getCorrectAnswer();
            boolean isCorrect = false;
            String judgeLabel; // 给前端显示的对错标签
            // 简答题(5)/名词解释(6) 为主观题：提交时不自动判分，等教师批改给分
            boolean subjective = q.getQuestionType() != null
                    && (q.getQuestionType() == 5 || q.getQuestionType() == 6);

            if (subjective) {
                isCorrect = false; // getScore 单独设 0
                judgeLabel = "主观题待教师批改";
                subjectiveCount++;
            } else {
                isCorrect = AnswerJudge.judge(q.getQuestionType(), userAnswer, correctAnswer);
                judgeLabel = isCorrect ? "正确" : "错误";
            }
            if (isCorrect) correctCount++; else wrongCount++;

            int getScore = 0;
            if (isCorrect && q.getScore() != null) {
                getScore = q.getScore();
            }
            scoreSum += getScore;

            // 错题归集（客观题 1/2/3/4 答错 → 归集；主观题 5/6 不归集）
            boolean collected = false;
            if (!isCorrect && q.getQuestionType() != null
                    && q.getQuestionType() != 5 && q.getQuestionType() != 6) {
                WrongQuestion w = new WrongQuestion();
                w.setUserId(userId);
                w.setQuestionId(q.getQuestionId());
                // courseId 从 paper 上拿（practice_paper.course_id）
                w.setCourseId(paper.getCourseId());
                w.setQuestionType(q.getQuestionType());
                w.setUserAnswer(userAnswer);
                w.setCorrectAnswer(correctAnswer);
                if (q.getScore() != null) {
                    w.setScore(BigDecimal.valueOf(q.getScore()));
                }
                wrongQuestionService.collect(w);
                collected = true;
                newlyCollected++;
            }

            // 落一条作答记录（练习卷整套，供「已练习题数」统计）
            StudentPracticeLog pl = new StudentPracticeLog();
            pl.setUserId(userId);
            pl.setQuestionId(q.getQuestionId());
            pl.setCourseId(paper.getCourseId());
            pl.setQuestionType(q.getQuestionType());
            pl.setSource(2); // 2=练习卷
            pl.setUserAnswer(userAnswer);
            pl.setIsCorrect(isCorrect ? 1 : 0);
            practiceLogMapper.insert(pl);

            // 答题明细（待 recordId 回填）
            PracticeAnswer pa = new PracticeAnswer();
            pa.setPracticePaperId(id);
            pa.setQuestionId(q.getQuestionId());
            pa.setSortNo(q.getSortNo());
            pa.setQuestionType(q.getQuestionType());
            pa.setScore(q.getScore() != null ? q.getScore() : 0);
            pa.setGetScore(getScore);
            pa.setUserAnswer(userAnswer);
            pa.setCorrectAnswer(correctAnswer);
            pa.setIsCorrect(isCorrect ? 1 : (subjective ? null : 0));
            // 批改相关：主观题标记待批改，客观题提交即视为已批改
            pa.setIsObjective(subjective ? 0 : 1);
            pa.setReviewStatus(subjective ? 0 : 1);
            answerRows.add(pa);

            // 判分明细（返回给前端展示）
            Map<String, Object> detail = new HashMap<>();
            detail.put("questionId", q.getQuestionId());
            detail.put("questionContent", q.getQuestionContent());
            detail.put("questionType", q.getQuestionType());
            detail.put("score", q.getScore());
            detail.put("getScore", getScore);
            detail.put("userAnswer", userAnswer);
            detail.put("correctAnswer", correctAnswer);
            detail.put("analysis", q.getAnalysis());
            detail.put("isCorrect", isCorrect);
            detail.put("judgeLabel", judgeLabel);
            detail.put("collected", collected);
            detailList.add(detail);
        }

        // 写练习记录
        PracticeRecord record = new PracticeRecord();
        record.setPracticePaperId(id);
        record.setStudentId(userId);
        record.setStudentName(displayName);
        record.setScore(scoreSum);
        record.setTotalScore(paper.getTotalScore() != null ? paper.getTotalScore() : scoreSum);
        record.setCorrectCount(correctCount);
        record.setWrongCount(wrongCount);
        record.setFinishStatus(1); // 完成
        // 含主观题 → 待教师批改（1），否则无需批改（0）
        record.setSubjectiveCount(subjectiveCount);
        record.setReviewStatus(subjectiveCount > 0 ? 1 : 0);
        record.setStartTime(LocalDateTime.now());
        record.setFinishTime(LocalDateTime.now());
        recordMapper.insert(record);

        // 回填 recordId 到答题明细，批量插入
        for (PracticeAnswer pa : answerRows) {
            pa.setPracticeRecordId(record.getId());
        }
        answerMapper.batchInsert(answerRows);

        // 组装返回
        Map<String, Object> resp = new HashMap<>();
        resp.put("recordId", record.getId());
        resp.put("paperName", paper.getPaperName());
        resp.put("score", scoreSum);
        resp.put("totalScore", paper.getTotalScore());
        resp.put("correctCount", correctCount);
        resp.put("wrongCount", wrongCount);
        resp.put("newlyCollected", newlyCollected);
        resp.put("subjectiveCount", subjectiveCount);
        // 0无需批改 1待批改：前端可据此提示"主观题待教师批改，成绩以批改后为准"
        resp.put("reviewStatus", record.getReviewStatus());
        resp.put("details", detailList);
        return Result.success("提交成功", resp);
    }

    /**
     * 学生端：提交文件型作业（教师上传了作业文件的作业：下载 → 线下作答 → 回传自己的作业文件）
     * POST /api/student/practice/papers/{id}/submit-attachment  （multipart/form-data）
     *   - file    学生做好的作业文件（必填，类型白名单与教师端附件一致）
     *   - remark  提交备注（选填，<=255 字）
     *
     * <p>同一份作业重复提交会覆盖上一版附件并重新回到「待批改」，教师端「批改作业」看到的就是最新一版。</p>
     */
    @PostMapping("/papers/{id}/submit-attachment")
    public Result<PracticeRecord> submitPaperAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "remark", required = false) String remark,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String userName = (String) request.getAttribute("userName");
        String realName = (String) request.getAttribute("realName");
        String displayName = (realName != null && !realName.isBlank()) ? realName : userName;
        PracticeRecord record = practiceService.submitPaperAttachment(userId, displayName, id, file, remark);
        return Result.success("提交成功，等待教师批改", record);
    }

    /**
     * 学生端列表：批量回填文件型作业的「我的提交」信息。
     * mySubmitId 由 PracticePaperMapper.listPage 的子查询带出，这里一次性批量查详情，避免 N+1。
     * mySubmitStatus：0 未提交 / 1 已提交待批改 / 2 已批改。
     */
    private void fillMySubmit(List<PracticePaper> list) {
        if (list == null || list.isEmpty()) return;
        List<Long> ids = list.stream()
                .map(PracticePaper::getMySubmitId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) return;
        Map<Long, PracticeRecord> map = recordMapper.listByIds(ids).stream()
                .collect(Collectors.toMap(PracticeRecord::getId, r -> r, (a, b) -> a));
        for (PracticePaper p : list) {
            PracticeRecord r = p.getMySubmitId() == null ? null : map.get(p.getMySubmitId());
            if (r == null) {
                p.setMySubmitStatus(0);
                continue;
            }
            p.setMySubmitStatus(r.getReviewStatus() != null && r.getReviewStatus() == 2 ? 2 : 1);
            p.setMySubmitAttachmentName(r.getAttachmentName());
            p.setMySubmitAttachmentPath(r.getAttachmentPath());
            p.setMySubmitAttachmentSize(r.getAttachmentSize());
            p.setMySubmitRemark(r.getSubmitRemark());
            p.setMySubmitTime(r.getFinishTime());
            p.setMySubmitScore(r.getScore());
            p.setMySubmitOpinion(r.getReviewOpinion());
        }
    }
}
