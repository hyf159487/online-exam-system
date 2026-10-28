package com.exam.exam_system.service.impl;

import com.exam.exam_system.dto.AiChatRequest;
import com.exam.exam_system.dto.AiExplainResult;
import com.exam.exam_system.dto.PaperQuestionItem;
import com.exam.exam_system.dto.StudentExamDetail;
import com.exam.exam_system.dto.StudentExamQuestion;
import com.exam.exam_system.dto.StudentRecordDetail;
import com.exam.exam_system.dto.SubmitRequest;
import com.exam.exam_system.dto.SubmitResult;
import com.exam.exam_system.entity.Exam;
import com.exam.exam_system.entity.ExamAnswer;
import com.exam.exam_system.entity.ExamRecord;
import com.exam.exam_system.entity.WrongQuestion;
import com.exam.exam_system.mapper.ExamAnswerMapper;
import com.exam.exam_system.mapper.ExamMapper;
import com.exam.exam_system.mapper.ExamRecordMapper;
import com.exam.exam_system.mapper.PaperQuestionMapper;
import com.exam.exam_system.mapper.WrongQuestionMapper;
import com.exam.exam_system.service.AiExplainService;
import com.exam.exam_system.service.RedisCacheService;
import com.exam.exam_system.service.StudentExamService;
import com.exam.exam_system.service.SysConfigService;
import com.exam.exam_system.service.WrongQuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学生端考试服务实现
 *
 * <p>判分规则：
 * <ul>
 *     <li>单选（1）/ 判断（3）/ 填空（4）：标准答案去空格后精确比较</li>
 *     <li>多选（2）：把学生答案按字母排序后再比（"C,A" 和 "A,C" 算对）</li>
 *     <li>简答（5）：主观题，isCorrect 留空、得 0 分，等教师阅卷</li>
 * </ul>
 */
@Service
public class StudentExamServiceImpl implements StudentExamService {

    @Autowired private ExamMapper examMapper;
    @Autowired private PaperQuestionMapper paperQuestionMapper;
    @Autowired private ExamRecordMapper examRecordMapper;
    @Autowired private ExamAnswerMapper examAnswerMapper;
    @Autowired private WrongQuestionService wrongQuestionService;
    @Autowired private AiExplainService aiExplainService;
    @Autowired private com.exam.exam_system.mapper.ExamMonitorMapper monitorMapper;

    @Autowired(required = false)
    private RedisCacheService redisCacheService;

    @Autowired
    private SysConfigService sysConfigService;

    /** 答题进度缓存 key 前缀 */
    private static final String PROGRESS_KEY_PREFIX = "exam:progress:";

    @Override
    public List<Exam> listExamsForStudent(Long userId) {
        List<Exam> exams = examMapper.listPublished();
        for (Exam e : exams) {
            e.setRuntimeStatus(deriveRuntimeStatus(e));
            // 标记我是否已交卷（myRecordId 非表字段，借用 Exam 的扩展字段区）
            ExamRecord mine = examRecordMapper.findByPaperAndUser(e.getPaperId(), userId);
            e.setMyRecordId(mine != null ? mine.getId() : null);
            e.setMyScore(mine != null ? mine.getTotalScore() : null);
        }
        return exams;
    }

    @Override
    public StudentExamDetail getExamDetail(Long examId, Long userId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null || exam.getStatus() == null || exam.getStatus() != 1) {
            throw new RuntimeException("考试不存在或未发布");
        }
        // 时间窗口校验：只能在做题时间内看题
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartTime())) {
            throw new RuntimeException("考试尚未开始");
        }
        if (now.isAfter(exam.getEndTime())) {
            throw new RuntimeException("考试已结束");
        }
        // 防重复：已交卷不能再拿题
        ExamRecord finished = examRecordMapper.findByPaperAndUser(exam.getPaperId(), userId);
        if (finished != null) {
            throw new RuntimeException("你已参加过该考试，不能重复作答");
        }
        // 创建/复用"进行中"考试记录：记录真实开考时间（刷新页面倒计时不重置）
        ExamRecord ongoing = examRecordMapper.findOngoingByPaperAndUser(exam.getPaperId(), userId);
        if (ongoing == null) {
            ongoing = new ExamRecord();
            ongoing.setPaperId(exam.getPaperId());
            ongoing.setUserId(userId);
            ongoing.setStartTime(now);
            ongoing.setStatus(0);                       // 0 = 考试中（未交卷）
            ongoing.setExamId(examId);
            examRecordMapper.insert(ongoing);           // 回填自增 id

            // 同步 exam_student 监控记录：学生一进考试页管理员端就可见
            List<PaperQuestionItem> pqItems = paperQuestionMapper.listByPaperId(exam.getPaperId());
            int totalCount = pqItems != null ? pqItems.size() : 0;
            com.exam.exam_system.entity.ExamStudent es = new com.exam.exam_system.entity.ExamStudent();
            es.setExamId(examId);
            es.setStudentId(userId);
            es.setStatus(1); // 考试中
            es.setAnsweredCount(0);
            es.setTotalCount(totalCount);
            int duration = exam.getDuration() != null ? exam.getDuration() * 60 : 3600;
            es.setRemainingTime(duration);
            es.setScreenSwitchCount(0);
            es.setEnterTime(now);
            try {
                monitorMapper.insertOrUpdate(es);
            } catch (Exception ignored) {
                // exam_student 表未创建时不影响学生答题主流程
            }
        }
        // 个人作答截止时间：min(开考时间 + 考试时长, 考试整体结束时间)
        LocalDateTime deadline = personalDeadline(ongoing.getStartTime(), exam);

        exam.setRuntimeStatus(deriveRuntimeStatus(exam));

        // 组装题目列表：复用组卷查询，但抹掉答案和解析
        List<PaperQuestionItem> items = paperQuestionMapper.listByPaperId(exam.getPaperId());
        List<StudentExamQuestion> questions = new ArrayList<>();
        for (PaperQuestionItem item : items) {
            StudentExamQuestion q = new StudentExamQuestion();
            q.setQuestionId(item.getQuestionId());
            q.setContent(item.getContent());
            q.setQuestionType(item.getQuestionType());
            q.setOptions(item.getOptions());
            q.setScore(item.getScore());
            q.setSortOrder(item.getSortOrder());
            questions.add(q);   // answer / analysis 不拷贝，绝不下发
        }

        StudentExamDetail detail = new StudentExamDetail();
        detail.setExam(exam);
        detail.setQuestions(questions);
        detail.setStartTime(ongoing.getStartTime());
        detail.setDeadline(deadline);
        detail.setDuration(exam.getDuration());

        // 下发系统配置：前端据此控制超时自动交卷、防作弊切屏检测
        detail.setExamTimeoutMinutes(sysConfigService.getInt("exam_timeout_minutes", 60));
        detail.setAntiCheatEnabled(sysConfigService.getBoolean("anti_cheat_enabled", true));

        // 下发已累计切屏次数：学生刷新页面后继续累计，不归零
        try {
            com.exam.exam_system.entity.ExamStudent esRow = monitorMapper.findByExamAndStudent(examId, userId);
            if (esRow != null && esRow.getScreenSwitchCount() != null) {
                detail.setScreenSwitchCount(esRow.getScreenSwitchCount());
            }
        } catch (Exception ignored) {
            // exam_student 表未创建时忽略
        }

        // 恢复已保存的答题进度（刷新页面 / 断线重连后接着做）
        // 优先读 Redis 缓存（最近一次保存的进度），未命中再回源数据库
        List<StudentExamDetail.AnswerItem> savedAnswers = null;
        if (redisCacheService != null) {
            savedAnswers = redisCacheService.getObject(
                    progressKey(exam.getId(), userId),
                    new TypeReference<List<StudentExamDetail.AnswerItem>>() {});
        }
        if (savedAnswers == null || savedAnswers.isEmpty()) {
            List<ExamAnswer> saved = examAnswerMapper.findByRecordId(ongoing.getId());
            if (saved != null && !saved.isEmpty()) {
                savedAnswers = saved.stream().map(a -> {
                    StudentExamDetail.AnswerItem item = new StudentExamDetail.AnswerItem();
                    item.setQuestionId(a.getQuestionId());
                    item.setAnswer(a.getUserAnswer());
                    return item;
                }).collect(Collectors.toList());
            }
        }
        if (savedAnswers != null && !savedAnswers.isEmpty()) {
            detail.setAnswers(savedAnswers);
        }
        return detail;
    }

    @Override
    public void saveProgress(Long examId, SubmitRequest req, Long userId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null || exam.getStatus() == null || exam.getStatus() != 1) {
            throw new RuntimeException("考试不存在或未发布");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartTime())) {
            throw new RuntimeException("考试尚未开始");
        }
        if (now.isAfter(exam.getEndTime())) {
            throw new RuntimeException("考试已结束，无法保存进度");
        }
        // 进行中记录：首次保存进度时自动创建（等价于进入考试）
        ExamRecord ongoing = examRecordMapper.findOngoingByPaperAndUser(exam.getPaperId(), userId);
        if (ongoing == null) {
            if (examRecordMapper.findByPaperAndUser(exam.getPaperId(), userId) != null) {
                throw new RuntimeException("考试已交卷，无法保存进度");
            }
            ongoing = new ExamRecord();
            ongoing.setPaperId(exam.getPaperId());
            ongoing.setExamId(examId);
            ongoing.setUserId(userId);
            ongoing.setStartTime(now);
            ongoing.setStatus(0);
            examRecordMapper.insert(ongoing);

            // 同步 exam_student 记录：标记为"考试中"（兜底分支：一般 getExamDetail 已经创建过，这里只处理先触发 saveProgress 的边缘场景）
            int totalCount = 0;
            List<PaperQuestionItem> paperItems = paperQuestionMapper.listByPaperId(exam.getPaperId());
            if (paperItems != null) {
                totalCount = paperItems.size();
            }
            com.exam.exam_system.entity.ExamStudent es = new com.exam.exam_system.entity.ExamStudent();
            es.setExamId(examId);
            es.setStudentId(userId);
            es.setStatus(1);
            es.setAnsweredCount(0);
            es.setTotalCount(totalCount);
            int duration = exam.getDuration() != null ? exam.getDuration() * 60 : 3600;
            es.setRemainingTime(duration);
            es.setScreenSwitchCount(0);
            es.setEnterTime(now);
            try {
                monitorMapper.insertOrUpdate(es);
            } catch (Exception ignored) {
                // exam_student 表未创建时不影响学生答题主流程
            }
        }

        // 逐题写入：已存在则更新，不存在则插入（幂等，不产生重复行）
        int answeredCount = 0;
        if (req.getAnswers() != null) {
            for (SubmitRequest.AnswerItem item : req.getAnswers()) {
                if (item.getQuestionId() == null) {
                    continue;
                }
                String ans = item.getAnswer() == null ? "" : item.getAnswer();
                if (!ans.isBlank()) {
                    answeredCount++;
                }
                ExamAnswer existing = examAnswerMapper.findByRecordAndQuestion(ongoing.getId(), item.getQuestionId());
                if (existing != null) {
                    if (!ans.equals(existing.getUserAnswer())) {
                        examAnswerMapper.updateAnswer(existing.getId(), ans);
                    }
                } else {
                    ExamAnswer row = new ExamAnswer();
                    row.setRecordId(ongoing.getId());
                    row.setQuestionId(item.getQuestionId());
                    row.setUserAnswer(ans);
                    examAnswerMapper.insert(row);
                }
            }
        }

        // 无论有没有答案变化，都更新 exam_student 的答题进度 + 剩余时间 + 切屏次数（心跳级实时同步）
        try {
            com.exam.exam_system.entity.ExamStudent es = monitorMapper.findByExamAndStudent(examId, userId);
            if (es != null) {
                int duration = exam.getDuration() != null ? exam.getDuration() * 60 : 3600;
                long elapsed = java.time.Duration.between(es.getEnterTime() != null ? es.getEnterTime() : now, now).getSeconds();
                // 剩余时间：前端传了就以前端为准（前端有倒计时和个人 deadline 偏移），未传时用后端按 enter_time 推导
                int remaining;
                if (req.getRemainingTime() != null) {
                    remaining = Math.max(0, Math.min(duration, req.getRemainingTime()));
                } else {
                    remaining = Math.max(0, (int) (duration - elapsed));
                }
                // 切屏次数：前端传了就用（取 max 防止回退），未传保留 DB 原值
                int screenSw = es.getScreenSwitchCount() == null ? 0 : es.getScreenSwitchCount();
                if (req.getScreenSwitchCount() != null) {
                    screenSw = Math.max(screenSw, req.getScreenSwitchCount());
                }
                // 已答题目数：answers 为空时（纯心跳）保留 DB 原 answeredCount，避免写 0 覆盖
                int ac = answeredCount;
                if ((req.getAnswers() == null || req.getAnswers().isEmpty()) && es.getAnsweredCount() != null) {
                    ac = Math.max(ac, es.getAnsweredCount());
                }
                monitorMapper.updateProgress(es.getId(), ac, remaining, screenSw);
            }
        } catch (Exception ignored) {
            // exam_student 表未创建时不影响学生答题主流程
        }

        // 把本次保存的进度写入 Redis 缓存（TTL 到考试结束，断线重连时快速恢复）
        if (redisCacheService != null) {
            long ttl = progressTtlSeconds(exam, now);
            if (ttl > 0) {
                redisCacheService.setObject(progressKey(exam.getId(), userId), req.getAnswers(), ttl);
            }
        }
    }

    @Override
    @Transactional   // 交卷 = 写 exam_record + N 条 exam_answer，要么全成功要么全回滚
    public SubmitResult submit(Long examId, SubmitRequest req, Long userId) {
        // 1. 校验考试状态
        Exam exam = examMapper.selectById(examId);
        if (exam == null || exam.getStatus() == null || exam.getStatus() != 1) {
            throw new RuntimeException("考试不存在或未发布");
        }
        // 宽限：交卷时只要求不在结束时间前太早（允许结束后 1 分钟内的网络延迟提交）
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartTime())) {
            throw new RuntimeException("考试尚未开始，不能交卷");
        }
        if (now.isAfter(exam.getEndTime().plusMinutes(1))) {
            throw new RuntimeException("考试已结束，交卷无效");
        }
        // 防重复交卷
        if (examRecordMapper.findByPaperAndUser(exam.getPaperId(), userId) != null) {
            throw new RuntimeException("你已参加过该考试，不能重复交卷");
        }
        // 必须有"进行中"记录：真实开考时间来自首次进入考试时创建的那条记录
        ExamRecord record = examRecordMapper.findOngoingByPaperAndUser(exam.getPaperId(), userId);
        if (record == null) {
            throw new RuntimeException("请先进入考试，再提交答卷");
        }
        // 个人作答时长校验：交卷时间不得超过 个人截止时间 + 1 分钟网络宽限
        LocalDateTime deadline = personalDeadline(record.getStartTime(), exam);
        if (now.isAfter(deadline.plusMinutes(1))) {
            throw new RuntimeException("考试时长已到，无法交卷");
        }

        // 2. 拉取试卷全部题目（含标准答案，仅在服务端内部使用）
        List<PaperQuestionItem> items = paperQuestionMapper.listByPaperId(exam.getPaperId());
        Map<Long, PaperQuestionItem> questionMap = items.stream()
                .collect(Collectors.toMap(PaperQuestionItem::getQuestionId, Function.identity()));

        // 学生答案按题目 ID 建索引，方便查找
        Map<Long, String> answerMap = req.getAnswers() == null ? Map.of() :
                req.getAnswers().stream()
                        .filter(a -> a.getQuestionId() != null)
                        .collect(Collectors.toMap(SubmitRequest.AnswerItem::getQuestionId,
                                a -> a.getAnswer() == null ? "" : a.getAnswer(),
                                (a, b) -> b));

        // 3. 逐题判分
        BigDecimal total = BigDecimal.ZERO;
        int subjective = 0;
        List<ExamAnswer> answerRows = new ArrayList<>();
        List<WrongQuestion> wrongList = new ArrayList<>();   // 客观题答错的，稍后归集进错题本

        for (PaperQuestionItem q : items) {
            ExamAnswer row = new ExamAnswer();
            row.setQuestionId(q.getQuestionId());
            String userAns = answerMap.getOrDefault(q.getQuestionId(), "");
            row.setUserAnswer(userAns);

            boolean isSubjective = q.getQuestionType() != null
                    && (q.getQuestionType() == 4 || q.getQuestionType() == 5 || q.getQuestionType() == 6);
            row.setIsObjective(isSubjective ? 0 : 1);
            row.setReviewStatus(isSubjective ? 0 : 1);

            if (isSubjective) {
                // 主观题（填空/简答），不自动判分，等教师阅卷
                subjective++;
                row.setIsCorrect(null);
                row.setGetScore(BigDecimal.ZERO);
            } else {
                boolean correct = judge(q.getQuestionType(), userAns, q.getAnswer());
                row.setIsCorrect(correct ? 1 : 0);
                BigDecimal got = correct ? q.getScore() : BigDecimal.ZERO;
                row.setGetScore(got);
                total = total.add(got);
                if (!correct) {
                    // 客观题答错 → 归入错题本
                    WrongQuestion wq = new WrongQuestion();
                    wq.setUserId(userId);
                    wq.setExamId(examId);
                    wq.setPaperId(exam.getPaperId());
                    wq.setQuestionId(q.getQuestionId());
                    wq.setCourseId(q.getCourseId() != null ? q.getCourseId() : exam.getCourseId());
                    wq.setQuestionType(q.getQuestionType());
                    wq.setUserAnswer(userAns);
                    wq.setCorrectAnswer(q.getAnswer());
                    wq.setScore(q.getScore());
                    wrongList.add(wq);
                }
            }
            answerRows.add(row);
        }

        // 4. 更新考试记录（exam_record）：保留首次进入考试时的 start_time
        boolean hasSubjective = subjective > 0;
        record.setSubmitTime(now);
        record.setTotalScore(total);
        record.setStatus(hasSubjective ? 1 : 2);        // 1=已交卷待阅卷 2=已判分
        record.setIsPass(total.compareTo(exam.getPassScore()) >= 0 ? 1 : 0);
        // 成绩快照：冗余试卷名/满分/及格分，防止考试/试卷被删除后成绩信息丢失
        record.setPaperName(exam.getPaperName());
        record.setPaperTotalScore(exam.getTotalScore());
        record.setPassScore(exam.getPassScore());
        examRecordMapper.updateSubmit(record);

        // 5. 写答题明细（exam_answer）
        //    先清掉进度保存产生的临时行（同一 record_id+question_id 不会重复），再写完整判分行
        examAnswerMapper.deleteByRecordId(record.getId());
        for (ExamAnswer row : answerRows) {
            row.setRecordId(record.getId());
            examAnswerMapper.insert(row);
        }

        // 5.5 客观题答错的归集进错题本（与交卷同一事务，要么都成功要么回滚）
        for (WrongQuestion wq : wrongList) {
            wq.setRecordId(record.getId());
            wrongQuestionService.collect(wq);
        }

        // 6. 交卷成功，清除答题进度缓存（防止再次进入考试时恢复旧答案）
        if (redisCacheService != null) {
            redisCacheService.delete(progressKey(exam.getId(), userId));
        }

        // 6.5 同步 exam_student 状态为"已交卷"
        try {
            com.exam.exam_system.entity.ExamStudent es = monitorMapper.findByExamAndStudent(examId, userId);
            if (es != null) {
                monitorMapper.updateStatus(es.getId(), 2);
            }
        } catch (Exception ignored) {
            // exam_student 表未创建时不影响学生交卷主流程
        }

        // 7. 返回结果
        SubmitResult result = new SubmitResult();
        result.setTotalScore(total);
        result.setPaperTotalScore(exam.getTotalScore());
        result.setPassScore(exam.getPassScore());
        result.setIsPass(record.getIsPass());
        result.setStatus(record.getStatus());
        result.setSubjectiveCount(subjective);
        return result;
    }

    @Override
    public List<ExamRecord> myRecords(Long userId) {
        return examRecordMapper.listByUser(userId);
    }

    // ==================== 试卷回看：AI 讲解 ====================

    @Override
    public AiExplainResult explainRecordQuestion(Long userId, Long recordId, Long questionId) {
        ExamRecord record = loadViewableRecord(recordId, userId);
        return aiExplainService.explainQuestion(questionId, myAnswerOf(record.getId(), questionId));
    }

    @Override
    public AiExplainResult chatRecordQuestion(Long userId, Long recordId, Long questionId,
                                              String question, List<AiChatRequest.Message> history) {
        ExamRecord record = loadViewableRecord(recordId, userId);
        return aiExplainService.chatQuestion(questionId, myAnswerOf(record.getId(), questionId),
                question, history);
    }

    /**
     * 我在某道题上的作答，作为 AI 讲解的错因上下文
     *
     * <p>从 exam_answer 取而不是信前端传参：讲解要针对真实作答，前端传上来的可能是改过的答案。
     * 未作答时返回 null，AI 会按「没作答」的口径讲记忆点与易错点。</p>
     */
    private String myAnswerOf(Long recordId, Long questionId) {
        if (questionId == null) {
            return null;
        }
        List<ExamAnswer> answers = examAnswerMapper.findByRecordId(recordId);
        if (answers == null) {
            return null;
        }
        return answers.stream()
                .filter(a -> questionId.equals(a.getQuestionId()))
                .map(ExamAnswer::getUserAnswer)
                .findFirst()
                .orElse(null);
    }

    // ==================== 试卷回看：门槛校验 ====================

    /**
     * 取一份「可回看的成绩记录」，并完成全部校验
     *
     * <p>试卷回看与 AI 讲解共用这一道门槛，避免两处规则各自演化：
     * 只能看自己的卷子、必须已交卷、且考试已结束——考试没结束时放行，
     * 等于把题目、答案连同讲解一起递给还在考的同学。</p>
     */
    private ExamRecord loadViewableRecord(Long recordId, Long userId) {
        if (recordId == null) {
            throw new RuntimeException("成绩记录ID不能为空");
        }
        ExamRecord record = examRecordMapper.selectById(recordId);
        if (record == null) {
            throw new RuntimeException("成绩记录不存在");
        }
        // 越权防护：只能看自己的卷子（recordId 可被前端伪造）
        if (!userId.equals(record.getUserId())) {
            throw new RuntimeException("无权查看他人的试卷");
        }
        // 未交卷的卷子不给看，避免考试中自己翻答案
        if (record.getStatus() == null || record.getStatus() < 1) {
            throw new RuntimeException("该试卷尚未交卷，暂不能查看");
        }
        // 考试未结束：整份试卷都不开放，参考答案、解析与 AI 讲解同理——
        // 同一场考试可能还有同学在作答，已交卷的人把题目、答案传出去同样影响公平。
        Exam exam = record.getExamId() == null ? null : examMapper.selectById(record.getExamId());
        boolean examFinished = exam == null || exam.getEndTime() == null
                || LocalDateTime.now().isAfter(exam.getEndTime());
        if (!examFinished) {
            throw new RuntimeException("本场考试结束后才能查看试卷");
        }
        return record;
    }

    @Override
    public StudentRecordDetail getRecordDetail(Long recordId, Long userId) {
        // 本人记录 + 已交卷 + 考试已结束，三道校验统一走 loadViewableRecord
        ExamRecord record = loadViewableRecord(recordId, userId);

        StudentRecordDetail detail = new StudentRecordDetail();
        detail.setRecordId(record.getId());
        detail.setExamId(record.getExamId());
        detail.setPaperId(record.getPaperId());
        detail.setPaperName(record.getPaperName());
        detail.setTotalScore(record.getTotalScore());
        detail.setPaperTotalScore(record.getPaperTotalScore());
        detail.setPassScore(record.getPassScore());
        detail.setIsPass(record.getIsPass());
        detail.setStatus(record.getStatus());
        detail.setSubmitTime(record.getSubmitTime());

        // 能走到这里说明考试已结束，答案与解析一律可见（无关联考试的记录同样放行）
        detail.setAnswerVisible(true);
        detail.setAnswerTip("已开放参考答案与解析，可对照订正错题");

        // 逐题组装：以试卷题目顺序为准，左连我的作答 + 阅卷结果
        List<StudentRecordDetail.QuestionItem> items = paperQuestionMapper.listForRecordDetail(record.getPaperId());
        Map<Long, ExamAnswer> answerMap = new HashMap<>();
        List<ExamAnswer> answers = examAnswerMapper.findByRecordId(recordId);
        if (answers != null) {
            for (ExamAnswer a : answers) {
                answerMap.put(a.getQuestionId(), a);
            }
        }

        int subjectiveCount = 0;
        int reviewedCount = 0;
        for (StudentRecordDetail.QuestionItem item : items) {
            ExamAnswer a = answerMap.get(item.getQuestionId());
            if (a != null) {
                item.setUserAnswer(a.getUserAnswer());
                item.setIsCorrect(a.getIsCorrect());
                item.setIsObjective(a.getIsObjective());
                item.setReviewStatus(a.getReviewStatus());
                item.setGetScore(a.getGetScore());
                item.setAnswered(a.getUserAnswer() != null && !a.getUserAnswer().isBlank());
                // 评语：优先新字段 review_opinion，兼容老数据落在 teacher_comment
                String comment = a.getReviewOpinion() != null && !a.getReviewOpinion().isBlank()
                        ? a.getReviewOpinion() : a.getTeacherComment();
                item.setTeacherComment(comment);
            } else {
                // 交卷时漏写的题目：兜底为未作答、0 分
                item.setAnswered(false);
                item.setGetScore(BigDecimal.ZERO);
            }

            // 主观题判定：优先用交卷时写入的 is_objective，老数据回退按题型判断（4填空/5简答/6其他）
            boolean subjective = item.getIsObjective() != null
                    ? item.getIsObjective() == 0
                    : (item.getQuestionType() != null && item.getQuestionType() >= 4);
            if (subjective) {
                subjectiveCount++;
                if (item.getReviewStatus() != null && item.getReviewStatus() == 1) {
                    reviewedCount++;
                } else {
                    // 待批阅：不下发 0 分，否则学生会误以为这题得了 0 分
                    item.setGetScore(null);
                    item.setTeacherComment(null);
                }
            }

        }

        boolean fullyReviewed = record.getStatus() == 2;
        detail.setQuestions(items);
        detail.setSubjectiveCount(subjectiveCount);
        detail.setReviewedCount(reviewedCount);
        detail.setFullyReviewed(fullyReviewed);

        // 排名：只有全部批阅完才有意义（否则同卷同学分数还没判完，排名失真）
        if (fullyReviewed && record.getPaperId() != null && record.getTotalScore() != null) {
            detail.setRank(examRecordMapper.countHigherScore(record.getPaperId(), record.getTotalScore()) + 1);
        }
        return detail;
    }

    // ========== 私有工具方法 ==========

    /** 答题进度缓存 key：exam:progress:{examId}:{userId} */
    private String progressKey(Long examId, Long userId) {
        return PROGRESS_KEY_PREFIX + examId + ":" + userId;
    }

    /**
     * 答题进度缓存的 TTL：距离考试结束的秒数 + 1 天冗余，
     * 保证断线重连期间缓存不提前过期。
     */
    private long progressTtlSeconds(Exam exam, LocalDateTime now) {
        if (exam.getEndTime() == null) {
            return 24 * 3600L;
        }
        long remain = java.time.Duration.between(now, exam.getEndTime()).getSeconds();
        return Math.max(remain, 60L) + 24 * 3600L;
    }

    /**
     * 个人作答截止时间 = min(开考时间 + 考试时长, 考试整体结束时间)。
     *
     * <p>晚进入考试的学生，作答截止不晚于考试整体结束时间；
     * 早进入的学生，按个人开考时间 + 时长计（刷新页面不重置）。
     */
    private LocalDateTime personalDeadline(LocalDateTime startTime, Exam exam) {
        int duration = exam.getDuration() == null ? 60 : exam.getDuration();
        LocalDateTime byDuration = startTime.plusMinutes(duration);
        LocalDateTime end = exam.getEndTime();
        return byDuration.isBefore(end) ? byDuration : end;
    }

    /** 运行状态推导：0未开始 1进行中 2已结束 */
    private Integer deriveRuntimeStatus(Exam e) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(e.getStartTime())) return 0;
        if (now.isAfter(e.getEndTime())) return 2;
        return 1;
    }

    /**
     * 客观题判分
     */
    private boolean judge(Integer questionType, String userAnswer, String standardAnswer) {
        if (standardAnswer == null || userAnswer == null) return false;
        String u = userAnswer.trim();
        String s = standardAnswer.trim();
        if (u.isEmpty()) return false;

        if (questionType != null && questionType == 2) {
            // 多选：拆分 → 去空格 → 排序 → 重新拼接后比较
            return normalizeMulti(u).equals(normalizeMulti(s));
        }
        // 单选 / 判断 / 填空：精确比较（不区分前后空格）
        return u.equals(s);
    }

    /** 多选答案标准化："C, A" → "A,C" */
    private String normalizeMulti(String raw) {
        return Arrays.stream(raw.split("[,，]"))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .sorted()
                .collect(Collectors.joining(","));
    }
}
