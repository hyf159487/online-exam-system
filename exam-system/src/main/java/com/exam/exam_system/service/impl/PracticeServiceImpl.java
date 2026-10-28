package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.BizException;
import com.exam.exam_system.dto.AiComposePlan;
import com.exam.exam_system.dto.CoursePracticeStats;
import com.exam.exam_system.dto.PracticeAttachment;
import com.exam.exam_system.dto.PracticeConfig;
import com.exam.exam_system.dto.PracticeQuestionQuery;
import com.exam.exam_system.dto.PracticeSubmitRequest;
import com.exam.exam_system.dto.PracticeSubmitResult;
import com.exam.exam_system.dto.QuestionPracticeStat;
import com.exam.exam_system.entity.PracticePaper;
import com.exam.exam_system.entity.PracticeRecord;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.entity.StudentPracticeLog;
import com.exam.exam_system.entity.SysUser;
import com.exam.exam_system.entity.WrongQuestion;
import com.exam.exam_system.mapper.CourseMemberMapper;
import com.exam.exam_system.mapper.PracticePaperMapper;
import com.exam.exam_system.mapper.PracticeRecordMapper;
import com.exam.exam_system.mapper.QuestionFavoriteMapper;
import com.exam.exam_system.mapper.QuestionMapper;
import com.exam.exam_system.mapper.StudentPracticeLogMapper;
import com.exam.exam_system.mapper.SysUserMapper;
import com.exam.exam_system.mapper.WrongQuestionMapper;
import com.exam.exam_system.service.PracticePaperService;
import com.exam.exam_system.service.PracticeService;
import com.exam.exam_system.service.WrongQuestionService;
import com.exam.exam_system.util.AnswerJudge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 自主练习服务实现 */
@Service
public class PracticeServiceImpl implements PracticeService {

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private QuestionFavoriteMapper questionFavoriteMapper;

    @Autowired
    private WrongQuestionMapper wrongQuestionMapper;

    @Autowired
    private WrongQuestionService wrongQuestionService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private StudentPracticeLogMapper practiceLogMapper;

    @Autowired
    private PracticePaperMapper practicePaperMapper;

    @Autowired
    private PracticeRecordMapper recordMapper;

    @Autowired
    private CourseMemberMapper courseMemberMapper;

    /** 复用教师端作业附件的落盘逻辑（类型白名单 + 大小上限 + /uploads/homework/ 存储规则） */
    @Autowired
    private PracticePaperService practicePaperService;

    @Override
    public List<Question> getPracticeQuestions(Long studentId, PracticeQuestionQuery query) {
        if (query == null) return List.of();
        String source = query.getSource() == null ? "" : query.getSource().trim().toLowerCase();
        boolean fromWrong = "wrong".equals(source);
        boolean fromFavorite = "favorite".equals(source);

        // 「我的错题 / 我的收藏」批量练习：抽题范围是学生自己的数据，题量可能不足 5 题，
        // 因此下限放宽到 1；同时允许不限学科（错题、收藏常常跨学科）。
        if (fromWrong || fromFavorite) {
            Integer n = query.getCount();
            if (n == null || n < 1) n = 10;
            if (n > 50) n = 50;
            if (fromWrong) {
                return wrongQuestionMapper.randomPickFromWrong(
                        studentId, query.getCourseId(), query.getQuestionTypes(),
                        query.getWrongCountMin(), query.getWrongCountMax(), n);
            }
            return questionFavoriteMapper.randomPickFromFavorite(
                    studentId, query.getCourseId(), query.getQuestionTypes(), n);
        }

        // 题库随机抽题必须指定学科，否则抽题范围不可控
        if (query.getCourseId() == null) return List.of();
        Integer count = query.getCount();
        if (count == null || count < 5) count = 10;    // 默认 10，下限 5
        if (count > 50) count = 50;                    // 上限 50
        return questionMapper.randomPickForPractice(
                query.getCourseId(),
                query.getQuestionTypes(),
                query.getDifficulties(),
                query.getKnowledgeId(),
                count,
                studentId);
    }

    @Override
    public PracticeSubmitResult submit(Long userId, PracticeSubmitRequest req) {
        Question q = questionMapper.selectById(req.getQuestionId());
        PracticeSubmitResult result = new PracticeSubmitResult();
        if (q == null) {
            result.setCorrect(false);
            return result;
        }
        result.setCorrectAnswer(q.getAnswer());
        result.setAnalysis(q.getAnalysis());

        boolean correct = AnswerJudge.judge(q.getQuestionType(), req.getUserAnswer(), q.getAnswer());
        result.setCorrect(correct);

        // 落一条作答记录（无论对错都记，用于统计该学科「已练习题数」）
        StudentPracticeLog log = new StudentPracticeLog();
        log.setUserId(userId);
        log.setQuestionId(q.getId());
        log.setCourseId(q.getCourseId());
        log.setQuestionType(q.getQuestionType());
        log.setSource(1); // 1=自由刷题单题
        log.setUserAnswer(req.getUserAnswer());
        log.setIsCorrect(correct ? 1 : 0);
        practiceLogMapper.insert(log);

        // 客观题（1/2/3/4）答错 → 归集进错题本；简答（5）不自动归集
        if (!correct && q.getQuestionType() != null && q.getQuestionType() != 5) {
            WrongQuestion w = new WrongQuestion();
            w.setUserId(userId);
            w.setQuestionId(q.getId());
            w.setCourseId(q.getCourseId());
            w.setQuestionType(q.getQuestionType());
            w.setUserAnswer(req.getUserAnswer());
            w.setCorrectAnswer(q.getAnswer());
            w.setScore(q.getScore());
            // examId / paperId / recordId 为 NULL（练习来源，非考试）
            wrongQuestionService.collect(w);
            result.setCollected(true);
        } else {
            result.setCollected(false);
        }

        // 来自「我的错题」的强化练习：判分后同步错题本（连续答对累计、达阈值自动移出）
        if (Boolean.TRUE.equals(req.getFromWrong())) {
            syncWrongBookAfterPractice(userId, q.getId(), correct, result);
        }
        return result;
    }

    /**
     * 错题强化练习答完一题后同步错题本状态。
     *
     * <p>与「错题本 - 单题重做」保持同一口径：答对 → correct_count +1，
     * 若学生开启了自动移除且达到阈值则移出错题本；答错 → 由上面的 collect 刷新错答并把
     * wrong_count +1，这里只回传「连续答对已清零」。</p>
     *
     * <p>本题已不在错题本（被手动移除、或上一轮已自动移出）时静默跳过，
     * 不能因为找不到记录就报错中断作答。</p>
     */
    private void syncWrongBookAfterPractice(Long userId, Long questionId, boolean correct,
                                            PracticeSubmitResult result) {
        WrongQuestion w = wrongQuestionMapper.findByUserAndQuestion(userId, questionId);
        if (w == null) return;
        if (!correct) {
            result.setCorrectCount(0);
            result.setRemoved(false);
            return;
        }
        wrongQuestionMapper.incrementCorrectCount(w.getId(), userId);
        int correctCount = (w.getCorrectCount() == null ? 0 : w.getCorrectCount()) + 1;
        result.setCorrectCount(correctCount);

        SysUser u = sysUserMapper.selectById(userId);
        boolean auto = u != null && u.getWrongAutoRemove() != null && u.getWrongAutoRemove() == 1;
        int threshold = normalizeThreshold(u == null ? null : u.getWrongRemoveThreshold());
        if (auto && correctCount >= threshold) {
            wrongQuestionMapper.deleteById(w.getId(), userId);
            result.setRemoved(true);
        } else {
            result.setRemoved(false);
        }
    }

    /** 阈值收敛到 1~10（默认 3），与错题本模块保持一致，避免历史脏数据导致策略失效 */
    private int normalizeThreshold(Integer t) {
        if (t == null) return 3;
        if (t < 1) return 1;
        return Math.min(t, 10);
    }

    @Override
    public PracticeConfig getConfig(Long userId) {
        SysUser u = sysUserMapper.selectById(userId);
        PracticeConfig cfg = new PracticeConfig();
        cfg.setWrongAutoRemove(u != null && u.getWrongAutoRemove() != null ? u.getWrongAutoRemove() : 0);
        Integer t = u == null ? null : u.getWrongRemoveThreshold();
        cfg.setWrongRemoveThreshold(t == null ? 3 : Math.max(1, Math.min(t, 10)));
        return cfg;
    }

    @Override
    public PracticeConfig saveConfig(Long userId, PracticeConfig cfg) {
        int auto = (cfg.getWrongAutoRemove() != null && cfg.getWrongAutoRemove() == 1) ? 1 : 0;
        // 阈值收敛到 1~10：连续答对几次自动移出（默认 3），前端为数字输入框
        Integer raw = cfg.getWrongRemoveThreshold();
        int threshold = normalizeThreshold(raw);
        sysUserMapper.updatePracticeConfig(userId, auto, threshold);
        PracticeConfig saved = new PracticeConfig();
        saved.setWrongAutoRemove(auto);
        saved.setWrongRemoveThreshold(threshold);
        return saved;
    }

    @Override
    public CoursePracticeStats courseStats(Long userId, Long courseId) {
        CoursePracticeStats stats = new CoursePracticeStats();
        // 题库总题数：学科下审核通过的题
        int total = (int) questionMapper.countByTypeAndDifficulty(courseId, null, null);
        // 该学生在该学科已作答过的去重题数
        int practiced = (int) practiceLogMapper.countDistinctByUserCourse(userId, courseId);
        stats.setTotalQuestion(total);
        stats.setPracticedQuestion(practiced);
        stats.setCoverageRate(percent(practiced, total));
        stats.setWrongQuestionCount((int) wrongQuestionMapper.countByUserAndCourse(userId, courseId));

        // 我的累计作答正确率（同一题的多次作答都计入，反映真实掌握程度）
        Map<String, Object> mine = practiceLogMapper.overviewByUserCourse(userId, courseId);
        int answered = mine == null ? 0 : toInt(mine.get("answered"));
        int correct = mine == null ? 0 : toInt(mine.get("correct"));
        stats.setMyAnswered(answered);
        stats.setMyCorrect(correct);
        stats.setMyAccuracy(answered > 0 ? percent(correct, answered) : null);

        // 一次分组查询同时喂两个图：按「题型 × 难度档」分组后分别汇总到题型、难度两个维度
        Map<Integer, Integer> typeCount = new LinkedHashMap<>();
        Map<Integer, Integer> levelCount = new LinkedHashMap<>();
        for (Map<String, Object> row : questionMapper.statTypeAndLevelForPractice(courseId)) {
            int cnt = toInt(row.get("cnt"));
            typeCount.merge(toInt(row.get("questionType")), cnt, Integer::sum);
            levelCount.merge(toInt(row.get("level")), cnt, Integer::sum);
        }

        // 题型分布：按题量倒序，占比由后端算好，前端图例直接用
        List<CoursePracticeStats.TypeStat> typeStats = new ArrayList<>();
        typeCount.forEach((type, cnt) -> {
            CoursePracticeStats.TypeStat ts = new CoursePracticeStats.TypeStat();
            ts.setQuestionType(type);
            ts.setQuestionTypeText(AiComposePlan.typeText(type));
            ts.setCount(cnt);
            ts.setRatio(percent(cnt, total));
            typeStats.add(ts);
        });
        typeStats.sort(Comparator.comparingInt(CoursePracticeStats.TypeStat::getCount).reversed());
        stats.setTypeStats(typeStats);

        // 难度分布：固定按「简单 → 中等 → 困难」输出、缺档补 0，条形图不会因学科不同而忽长忽短
        List<CoursePracticeStats.LevelStat> levelStats = new ArrayList<>();
        for (int level = 1; level <= 3; level++) {
            int cnt = levelCount.getOrDefault(level, 0);
            CoursePracticeStats.LevelStat ls = new CoursePracticeStats.LevelStat();
            ls.setLevel(level);
            ls.setLevelText(difficultyText(level));
            ls.setCount(cnt);
            ls.setRatio(percent(cnt, total));
            levelStats.add(ls);
        }
        stats.setLevelStats(levelStats);

        // 知识点题量 Top5（该学科未维护知识点时为空，前端自动隐藏该区块）
        List<CoursePracticeStats.KnowledgeStat> knowledgeStats = new ArrayList<>();
        for (Map<String, Object> row : questionMapper.statByKnowledge(courseId)) {
            if (knowledgeStats.size() >= 5) break;
            CoursePracticeStats.KnowledgeStat ks = new CoursePracticeStats.KnowledgeStat();
            Object kid = row.get("knowledgeId");
            ks.setKnowledgeId(kid instanceof Number ? ((Number) kid).longValue() : null);
            Object name = row.get("knowledgeName");
            ks.setKnowledgeName(name == null ? null : String.valueOf(name));
            ks.setCount(toInt(row.get("cnt")));
            knowledgeStats.add(ks);
        }
        stats.setKnowledgeStats(knowledgeStats);
        return stats;
    }

    /** 占比(%)：分母为 0 时返回 0，避免除零把画像统计整块打挂 */
    private int percent(int part, int whole) {
        if (whole <= 0) return 0;
        return Math.round(part * 100f / whole);
    }

    /** Map 里的 COUNT 是 Number 子类（Integer / Long / BigInteger），统一转 int */
    private int toInt(Object v) {
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    /** 难度档中文名（与前端刷题筛选的三档口径一致） */
    private String difficultyText(int level) {
        return switch (level) {
            case 1 -> "简单";
            case 2 -> "中等";
            default -> "困难";
        };
    }

    @Override
    public QuestionPracticeStat questionStat(Long userId, Long questionId) {
        QuestionPracticeStat s = new QuestionPracticeStat();
        s.setDoneCount(practiceLogMapper.countByUserQuestion(userId, questionId));
        s.setCorrectCount(practiceLogMapper.countCorrectByUserQuestion(userId, questionId));
        return s;
    }

    // ========== 文件型作业：学生提交 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PracticeRecord submitPaperAttachment(Long studentId, String studentName, Long paperId,
                                                MultipartFile file, String remark) {
        if (studentId == null) {
            throw new BizException("登录状态异常，请重新登录");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要提交的作业文件");
        }
        PracticePaper paper = practicePaperMapper.selectById(paperId);
        if (paper == null || (paper.getIsDeleted() != null && paper.getIsDeleted() == 1)) {
            throw new BizException("作业不存在或已删除");
        }
        if (paper.getStatus() == null || paper.getStatus() != 1) {
            throw new BizException("该作业尚未发布，暂不可提交");
        }
        // 只有附件型作业（教师上传了作业文件）才需要学生提交文件；在线作业走在线作答
        if (paper.getAttachmentPath() == null || paper.getAttachmentPath().isBlank()) {
            throw new BizException("该作业为在线作业，请点击「开始做题」在线作答");
        }
        List<Long> joined = courseMemberMapper.selectJoinedCourseIds(studentId);
        if (paper.getCourseId() == null || joined == null || !joined.contains(paper.getCourseId())) {
            throw new BizException("只能提交已加入课程的作业");
        }
        String safeRemark = remark == null ? null : remark.trim();
        if (safeRemark != null && safeRemark.length() > 255) {
            safeRemark = safeRemark.substring(0, 255);
        }

        // 文件落盘：复用教师端附件的校验与存储规则（类型白名单 + 300MB 上限 + UUID 文件名）
        PracticeAttachment att;
        try {
            att = practicePaperService.uploadAttachment(file);
        } catch (IllegalArgumentException e) {
            throw new BizException(e.getMessage());
        }

        // 同一份作业重复提交：覆盖上一条附件（沿用记录ID，教师端直接看到最新一版）
        PracticeRecord exist = recordMapper.selectMyAttachmentSubmit(paperId, studentId);
        PracticeRecord record = exist != null ? exist : new PracticeRecord();
        record.setPracticePaperId(paperId);
        record.setStudentId(studentId);
        record.setStudentName(studentName);
        record.setTotalScore(paper.getTotalScore() == null ? 0 : paper.getTotalScore());
        record.setAttachmentName(att.getFileName());
        record.setAttachmentPath(att.getFilePath());
        record.setAttachmentSize(att.getFileSize());
        record.setAttachmentType(att.getFileType());
        record.setSubmitRemark(safeRemark);
        if (exist == null) {
            recordMapper.insertAttachmentSubmit(record);
        } else {
            recordMapper.updateAttachmentSubmit(record);
        }
        // 回读一次：拿到提交时间与批改状态，前端刷新后直接展示「已提交」
        return recordMapper.selectMyAttachmentSubmit(paperId, studentId);
    }
}
