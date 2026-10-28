package com.exam.exam_system.service.impl;

import com.exam.exam_system.dto.*;
import com.exam.exam_system.mapper.StatsClassMapper;
import com.exam.exam_system.service.StatsClassService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 班级学情统计实现
 *
 * <p>所有及格 / 优秀 / 分数段计算均基于试卷真实满分动态进行：
 * 及格线 = 满分 × 0.6，优秀线 = 满分 × 0.85；分数段采用百分比分段（方案 A），
 * 兼容 50 / 100 / 120 / 150 等不同满分规格试卷。</p>
 */
@Service
public class StatsClassServiceImpl implements StatsClassService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal SIXTY = new BigDecimal("60");
    private static final BigDecimal SEVENTY = new BigDecimal("70");
    private static final BigDecimal EIGHTY = new BigDecimal("80");
    private static final BigDecimal NINETY = new BigDecimal("90");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private StatsClassMapper statsClassMapper;

    @Override
    public List<StatsExamVO> getExamList(Long teacherId, Integer role) {
        return statsClassMapper.listExams(teacherId, role);
    }

    @Override
    public List<ClassStatsQuery> getClassList(Long examId, Long teacherId, Integer role) {
        checkTeacherExamAccess(examId, teacherId, role);
        return statsClassMapper.listClassesByExam(examId);
    }

    // ==================== 核心统计 ====================

    @Override
    public ClassStatsVO getClassStats(ClassStatsQuery query) {
        Long examId = query.getExamId();
        Long classId = query.getClassId();
        if (examId == null || classId == null) {
            throw new IllegalArgumentException("请选择考试与班级后再查询");
        }

        StatsClassMapper.ExamMetaRow meta = statsClassMapper.getExamMeta(examId);
        if (meta == null) {
            throw new IllegalArgumentException("考试不存在或尚未结束，无法统计");
        }
        BigDecimal fullScore = meta.getFullScore();
        if (fullScore == null || fullScore.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("该考试关联的试卷满分异常（0 分），无法进行学情统计");
        }

        // 权限：教师只能统计本人创建的考试
        checkTeacherExamAccess(examId, query.getTeacherId(), query.getRole());

        // 1. 名单口径：
        //    应考名单 = 本班学生 ∩ 已加入本考试所属课程
        //    （部分学生虽在同一班级，但并未加入本课程，不参加本课程考试，不计入应考/缺考）
        //    另补入「本班已有成绩但不在课程名单内」的学生（退课 / 历史数据），避免已考成绩被漏统计
        Long courseId = meta.getCourseId();
        List<StatsClassMapper.StudentRow> classRoster = statsClassMapper.listClassStudents(classId);
        int classSize = classRoster.size();
        int courseSize;
        List<StatsClassMapper.StudentRow> roster;
        if (courseId != null) {
            roster = statsClassMapper.listClassStudentsInCourse(classId, courseId);
            courseSize = statsClassMapper.countCourseMembers(courseId);
        } else {
            // 考试未关联课程：退回班级花名册口径
            roster = classRoster;
            courseSize = classSize;
        }
        Map<Long, StatsClassMapper.StudentRow> rosterMap = new LinkedHashMap<>();
        for (StatsClassMapper.StudentRow s : roster) {
            rosterMap.put(s.getStudentId(), s);
        }

        Map<Long, StatsClassMapper.RecordRow> allRecords = new HashMap<>();
        for (StatsClassMapper.RecordRow r : statsClassMapper.listExamRecordsByPaper(meta.getPaperId())) {
            allRecords.put(r.getUserId(), r); // 同一学生多条时取后者（更新的记录）
        }
        for (StatsClassMapper.StudentRow s : classRoster) {
            StatsClassMapper.RecordRow rec = allRecords.get(s.getStudentId());
            if (!rosterMap.containsKey(s.getStudentId()) && rec != null && rec.getTotalScore() != null) {
                rosterMap.put(s.getStudentId(), s);
            }
        }
        roster = new ArrayList<>(rosterMap.values());

        Map<Long, StatsClassMapper.RecordRow> recordByUser = new HashMap<>();
        for (Map.Entry<Long, StatsClassMapper.RecordRow> e : allRecords.entrySet()) {
            if (rosterMap.containsKey(e.getKey()) && e.getValue().getTotalScore() != null) {
                recordByUser.put(e.getKey(), e.getValue());
            }
        }

        // 2. 动态阈值
        BigDecimal passLine = fullScore.multiply(new BigDecimal("0.6")).setScale(1, RoundingMode.HALF_UP);
        BigDecimal excellentLine = fullScore.multiply(new BigDecimal("0.85")).setScale(1, RoundingMode.HALF_UP);

        // 3. 学生名单排序统计（参考在前按分数降序，缺考在后按学号）
        List<StatsClassMapper.StudentRow> participants = new ArrayList<>();
        List<StatsClassMapper.StudentRow> absentStudents = new ArrayList<>();
        for (StatsClassMapper.StudentRow s : roster) {
            if (recordByUser.containsKey(s.getStudentId())) {
                participants.add(s);
            } else {
                absentStudents.add(s);
            }
        }
        participants.sort((a, b) -> {
            int cmp = recordByUser.get(b.getStudentId()).getTotalScore()
                    .compareTo(recordByUser.get(a.getStudentId()).getTotalScore());
            return cmp != 0 ? cmp : a.getUsername().compareTo(b.getUsername());
        });
        absentStudents.sort(Comparator.comparing(StatsClassMapper.StudentRow::getUsername));

        // 4. 统计卡片指标
        int candidateCount = roster.size();   // 应考人数（本班已加入该课程的学生数）
        int studentCount = participants.size();
        int absentCount = Math.max(0, candidateCount - studentCount);

        BigDecimal sumScore = ZERO;
        BigDecimal maxScore = null;
        BigDecimal minScore = null;
        int passCount = 0;
        int excellentCount = 0;
        List<BigDecimal> allScores = new ArrayList<>();

        for (StatsClassMapper.StudentRow s : participants) {
            BigDecimal score = recordByUser.get(s.getStudentId()).getTotalScore();
            allScores.add(score);
            sumScore = sumScore.add(score);
            maxScore = maxScore == null ? score : maxScore.max(score);
            minScore = minScore == null ? score : minScore.min(score);
            if (score.compareTo(passLine) >= 0) {
                passCount++;
            }
            if (score.compareTo(excellentLine) >= 0) {
                excellentCount++;
            }
        }

        BigDecimal avgScore = null;
        if (studentCount > 0) {
            avgScore = sumScore.divide(new BigDecimal(studentCount), 1, RoundingMode.HALF_UP);
        }
        int failCount = studentCount - passCount;
        BigDecimal passRate = percent(passCount, studentCount);
        BigDecimal excellentRate = percent(excellentCount, studentCount);

        // 5. 分数段分布（百分比分段，方案 A）
        ClassStatsVO vo = new ClassStatsVO();
        vo.setExamId(examId);
        vo.setExamName(meta.getExamName());
        vo.setCourseName(meta.getCourseName());
        vo.setClassName(statsClassMapper.selectClassName(classId));
        vo.setFullScore(fullScore);
        vo.setPassLine(passLine);
        vo.setExcellentLine(excellentLine);
        vo.setClassSize(classSize);
        vo.setCourseSize(courseSize);
        vo.setCandidateCount(candidateCount);
        vo.setStudentCount(studentCount);
        vo.setAbsentCount(absentCount);
        vo.setAvgScore(avgScore);
        vo.setMaxScore(maxScore);
        vo.setMinScore(minScore);
        vo.setPassCount(passCount);
        vo.setFailCount(failCount);
        vo.setExcellentCount(excellentCount);
        vo.setPassRate(passRate);
        vo.setExcellentRate(excellentRate);

        // 百分比区间: 0-59, 60-69, 70-79, 80-89, 90-100
        int[][] segs = {{0, 59}, {60, 69}, {70, 79}, {80, 89}, {90, 100}};
        String[] segNames = {"0-59%", "60-69%", "70-79%", "80-89%", "90-100%"};
        int[] counts = new int[5];
        for (BigDecimal score : allScores) {
            BigDecimal pct = score.multiply(ONE_HUNDRED).divide(fullScore, 6, RoundingMode.HALF_UP);
            int idx = 4;
            if (pct.compareTo(SIXTY) < 0) idx = 0;
            else if (pct.compareTo(SEVENTY) < 0) idx = 1;
            else if (pct.compareTo(EIGHTY) < 0) idx = 2;
            else if (pct.compareTo(NINETY) < 0) idx = 3;
            counts[idx]++;
        }
        List<ClassStatsVO.ScoreRangeItem> ranges = new ArrayList<>();
        for (int i = 0; i < segs.length; i++) {
            ClassStatsVO.ScoreRangeItem item = new ClassStatsVO.ScoreRangeItem();
            item.setRangeName(segNames[i]);
            item.setCount(counts[i]);
            item.setRatio(percent(counts[i], studentCount));
            BigDecimal lo = fullScore.multiply(new BigDecimal(segs[i][0])).divide(ONE_HUNDRED)
                    .setScale(1, RoundingMode.HALF_UP);
            BigDecimal hi = segs[i][1] == 100
                    ? fullScore
                    : fullScore.multiply(new BigDecimal(segs[i][1])).divide(ONE_HUNDRED)
                    .setScale(1, RoundingMode.HALF_UP);
            item.setActualText(fmt(lo) + "~" + fmt(hi) + " 分");
            ranges.add(item);
        }
        vo.setScoreRanges(ranges);

        // 6. 知识点正确率（比值不受满分影响）
        List<ClassStatsVO.KnowledgeStatsItem> kStats = new ArrayList<>();
        for (StatsClassMapper.KnowledgeStatsRow row : statsClassMapper.listKnowledgeStats(examId, classId)) {
            ClassStatsVO.KnowledgeStatsItem item = new ClassStatsVO.KnowledgeStatsItem();
            item.setKnowledgeId(row.getKnowledgeId());
            item.setKnowledgeName(row.getKnowledgeName());
            item.setTotalCount(row.getTotalCount());
            item.setCorrectCount(row.getCorrectCount());
            int total = row.getTotalCount() == null ? 0 : row.getTotalCount();
            int correct = row.getCorrectCount() == null ? 0 : row.getCorrectCount();
            item.setCorrectRate(total == 0 ? ZERO
                    : new BigDecimal(correct).multiply(ONE_HUNDRED)
                    .divide(new BigDecimal(total), 2, RoundingMode.HALF_UP));
            kStats.add(item);
        }
        vo.setKnowledgeStats(kStats);

        // 7. 学生成绩明细（含缺考）
        List<ClassStatsVO.StudentScoreItem> rows = new ArrayList<>();
        int rank = 0;
        for (StatsClassMapper.StudentRow s : participants) {
            rank++;
            StatsClassMapper.RecordRow rec = recordByUser.get(s.getStudentId());
            ClassStatsVO.StudentScoreItem row = new ClassStatsVO.StudentScoreItem();
            row.setStudentId(s.getStudentId());
            row.setRecordId(rec.getId());
            row.setRealName(s.getRealName());
            row.setUsername(s.getUsername());
            row.setTotalScore(rec.getTotalScore());
            row.setAbsent(false);
            row.setRank(rank);
            row.setIsPass(rec.getTotalScore().compareTo(passLine) >= 0 ? 1 : 0);
            row.setUsedMinutes(calcMinutes(rec.getStartTime(), rec.getSubmitTime()));
            row.setSubmitTime(formatTime(rec.getSubmitTime()));
            rows.add(row);
        }
        for (StatsClassMapper.StudentRow s : absentStudents) {
            ClassStatsVO.StudentScoreItem row = new ClassStatsVO.StudentScoreItem();
            row.setStudentId(s.getStudentId());
            row.setRecordId(null);
            row.setRealName(s.getRealName());
            row.setUsername(s.getUsername());
            row.setTotalScore(null);
            row.setAbsent(true);
            row.setRank(null);
            row.setIsPass(null);
            row.setUsedMinutes(null);
            row.setSubmitTime(null);
            rows.add(row);
        }
        vo.setStudentScores(rows);

        return vo;
    }

    // ==================== 学生逐题作答详情 ====================

    @Override
    public StudentAnswerVO getStudentAnswerDetail(Long examId, Long studentId, Long teacherId, Integer role) {
        if (examId == null || studentId == null) {
            throw new IllegalArgumentException("参数缺失：考试或学生不能为空");
        }
        StatsClassMapper.ExamMetaRow meta = statsClassMapper.getExamMeta(examId);
        if (meta == null) {
            throw new IllegalArgumentException("考试不存在或尚未结束");
        }
        checkTeacherExamAccess(examId, teacherId, role);

        StatsClassMapper.RecordRow rec = statsClassMapper.findStudentRecord(meta.getPaperId(), studentId);
        if (rec == null) {
            throw new IllegalArgumentException("该学生未参加本次考试（缺考），无作答记录");
        }

        StudentAnswerVO vo = new StudentAnswerVO();
        vo.setStudentId(studentId);
        vo.setRealName(rec.getRealName());
        vo.setUsername(rec.getUsername());
        vo.setTotalScore(rec.getTotalScore());
        vo.setFullScore(meta.getFullScore());
        vo.setSubmitTime(formatTime(rec.getSubmitTime()));
        vo.setUsedMinutes(calcMinutes(rec.getStartTime(), rec.getSubmitTime()));
        vo.setRecordStatus(rec.getStatus());

        List<StatsClassMapper.AnswerRow> rawAnswers = statsClassMapper.listPaperAnswers(rec.getId(), meta.getPaperId());
        List<StudentAnswerVO.AnswerItem> items = new ArrayList<>();
        int correctCount = 0;
        int wrongCount = 0;
        int pendingCount = 0;
        int order = 0;
        for (StatsClassMapper.AnswerRow a : rawAnswers) {
            order++;
            StudentAnswerVO.AnswerItem item = new StudentAnswerVO.AnswerItem();
            item.setSortOrder(order);
            item.setQuestionId(a.getQuestionId());
            item.setQuestionType(a.getQuestionType());
            item.setContent(a.getContent());
            item.setOptions(a.getOptions());
            item.setStandardAnswer(a.getStandardAnswer());
            item.setAnalysis(a.getAnalysis());
            item.setKnowledgeName(a.getKnowledgeName());
            item.setQuestionScore(a.getQuestionScore());
            item.setUserAnswer(a.getUserAnswer());
            item.setGetScore(a.getGetScore());
            item.setIsCorrect(a.getIsCorrect());

            boolean manual = a.getQuestionType() != null
                    && (a.getQuestionType() == 4 || a.getQuestionType() == 5 || a.getQuestionType() == 6);
            BigDecimal getScore = a.getGetScore() == null ? ZERO : a.getGetScore();

            if (a.getIsCorrect() != null) {
                // 已明确判对错（客观题自动判 / 教师阅卷后）
                item.setCorrect(a.getIsCorrect() == 1);
            } else if (manual) {
                // 主观题：已判分但没写 is_correct 时按得分判断
                if (getScore.compareTo(ZERO) > 0) {
                    item.setCorrect(true);
                } else if (rec.getStatus() != null && rec.getStatus() >= 2) {
                    // 已整体判分但该题为 0 分
                    item.setCorrect(false);
                } else {
                    item.setCorrect(null); // 待阅卷
                }
            } else {
                // 客观题且无判分标记：得分>0 视为对，否则错（未作答也算错）
                item.setCorrect(getScore.compareTo(ZERO) > 0);
            }

            if (Boolean.TRUE.equals(item.getCorrect())) {
                correctCount++;
            } else if (Boolean.FALSE.equals(item.getCorrect())) {
                wrongCount++;
            } else {
                pendingCount++;
            }
            // 空选项容错：题干展示
            items.add(item);
        }

        vo.setQuestionCount(rawAnswers.size());
        vo.setCorrectCount(correctCount);
        vo.setWrongCount(wrongCount);
        vo.setPendingCount(pendingCount);
        vo.setAnswers(items);
        return vo;
    }

    // ==================== 作答维度统计（题型 / 难度） ====================

    @Override
    public DimensionStatsVO getDimensionStats(ClassStatsQuery query, String mode) {
        if (query == null || query.getExamId() == null || query.getClassId() == null) {
            throw new IllegalArgumentException("参数缺失：请选择考试与班级");
        }
        checkTeacherExamAccess(query.getExamId(), query.getTeacherId(), query.getRole());
        return buildDimensionStats(query.getExamId(), query.getClassId(), mode);
    }

    private DimensionStatsVO buildDimensionStats(Long examId, Long classId, String mode) {
        boolean byDifficulty = "difficulty".equalsIgnoreCase(mode);
        List<StatsClassMapper.DimensionStatsRow> raw = byDifficulty
                ? statsClassMapper.listDifficultyStats(examId, classId)
                : statsClassMapper.listQuestionTypeStats(examId, classId);

        // 聚合（难度模式把 1~5 归并为 简单/中等/困难 三档）
        Map<Integer, int[]> agg = new LinkedHashMap<>();
        List<Integer> order = new ArrayList<>();
        for (StatsClassMapper.DimensionStatsRow row : raw) {
            Integer typeKey = row.getTypeKey();
            if (typeKey == null) continue;
            Integer aggKey = byDifficulty ? difficultyBucket(typeKey) : typeKey;
            int total = row.getTotalCount() == null ? 0 : row.getTotalCount();
            int correct = row.getCorrectCount() == null ? 0 : row.getCorrectCount();
            int[] bucket = agg.get(aggKey);
            if (bucket == null) {
                agg.put(aggKey, new int[]{total, correct});
                order.add(aggKey);
            } else {
                bucket[0] += total;
                bucket[1] += correct;
            }
        }

        DimensionStatsVO vo = new DimensionStatsVO();
        vo.setDimension(byDifficulty ? "difficulty" : "type");
        List<DimensionStatsVO.Item> items = new ArrayList<>();
        for (Integer aggKey : order) {
            int[] bucket = agg.get(aggKey);
            DimensionStatsVO.Item item = new DimensionStatsVO.Item();
            item.setKey(aggKey);
            item.setName(byDifficulty ? difficultyNameOf(aggKey) : typeNameOf(aggKey));
            item.setTotalCount(bucket[0]);
            item.setCorrectCount(bucket[1]);
            item.setCorrectRate(bucket[0] == 0 ? ZERO
                    : new BigDecimal(bucket[1]).multiply(ONE_HUNDRED)
                    .divide(new BigDecimal(bucket[0]), 2, RoundingMode.HALF_UP));
            items.add(item);
        }
        vo.setItems(items);
        return vo;
    }

    // ==================== Excel 导出 ====================

    @Override
    public void exportClassStatsExcel(ClassStatsQuery query, HttpServletResponse response) {
        ClassStatsVO vo = getClassStats(query);
        if (vo.getFullScore() == null || vo.getFullScore().compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("试卷满分异常，无法导出");
        }
        Long dimExamId = query.getExamId();
        Long dimClassId = query.getClassId();

        try {
            XSSFWorkbook workbook = new XSSFWorkbook();
            CellStyle titleStyle = style(workbook, true, IndexedColors.DARK_BLUE, (short) 14);
            CellStyle headerStyle = style(workbook, true, IndexedColors.WHITE, (short) 11);
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle cellStyle = style(workbook, false, null, (short) 11);
            CellStyle warnStyle = style(workbook, false, IndexedColors.RED, (short) 11);

            // ---------- Sheet1 统计指标 ----------
            Sheet s1 = workbook.createSheet("统计指标");
            Row t = s1.createRow(0);
            t.createCell(0).setCellValue("《" + vo.getExamName() + "》班级学情统计报表");
            t.getCell(0).setCellStyle(titleStyle);
            s1.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

            int r = 1;
            createInfoRow(s1, r++, "试卷满分：" + fmt(vo.getFullScore()) + " 分", null, cellStyle);
            createInfoRow(s1, r++, "课程 / 班级：" + vo.getCourseName() + " / " + vo.getClassName(), null, cellStyle);
            createInfoRow(s1, r++, "及格线：≥" + fmt(vo.getPassLine()) + " 分（满分 × 60%）",
                    "优秀线：≥" + fmt(vo.getExcellentLine()) + " 分（满分 × 85%）", cellStyle);
            createInfoRow(s1, r++, "课程人数：" + nvl(vo.getCourseSize()),
                    "应考人数：" + nvl(vo.getCandidateCount()) + "（本班已加入该课程的学生数）", cellStyle);
            createInfoRow(s1, r++, "参考人数：" + nvl(vo.getStudentCount()) + "（缺考 " + nvl(vo.getAbsentCount()) + " 人）",
                    "班级在册：" + nvl(vo.getClassSize()) + " 人", cellStyle);
            createInfoRow(s1, r++, "平均分：" + fmt(vo.getAvgScore()),
                    "最高分：" + fmt(vo.getMaxScore()) + " / 最低分：" + fmt(vo.getMinScore()), cellStyle);
            createInfoRow(s1, r++, "及格人数：" + nvl(vo.getPassCount()) + "（及格率 " + fmt(vo.getPassRate()) + "%）",
                    "不及格人数：" + nvl(vo.getFailCount()), cellStyle);
            createInfoRow(s1, r, "优秀人数：" + nvl(vo.getExcellentCount()) + "（优秀率 " + fmt(vo.getExcellentRate()) + "%）",
                    null, cellStyle);
            for (int i = 0; i < 4; i++) {
                s1.setColumnWidth(i, 22 * 256);
            }

            // ---------- Sheet2 分数段分布 ----------
            Sheet s2 = workbook.createSheet("分数段分布");
            writeHeader(s2, headerStyle, "百分比分数段", "对应实际分数", "人数", "占参考人数比例");
            List<ClassStatsVO.ScoreRangeItem> ranges = vo.getScoreRanges();
            if (ranges != null) {
                int rr = 1;
                for (ClassStatsVO.ScoreRangeItem it : ranges) {
                    Row row = s2.createRow(rr++);
                    row.createCell(0).setCellValue(it.getRangeName());
                    row.createCell(1).setCellValue(it.getActualText());
                    row.createCell(2).setCellValue(it.getCount() == null ? 0 : it.getCount());
                    row.createCell(3).setCellValue(fmt(it.getRatio()) + "%");
                    for (int i = 0; i < 4; i++) {
                        row.getCell(i).setCellStyle(cellStyle);
                    }
                }
            }
            for (int i = 0; i < 4; i++) {
                s2.setColumnWidth(i, 22 * 256);
            }

            // ---------- Sheet3 知识点正确率 ----------
            Sheet s3 = workbook.createSheet("知识点正确率");
            writeHeader(s3, headerStyle, "知识点名称", "总答题数", "答对题数", "正确率");
            List<ClassStatsVO.KnowledgeStatsItem> kStats = vo.getKnowledgeStats();
            if (kStats != null) {
                int rr = 1;
                for (ClassStatsVO.KnowledgeStatsItem it : kStats) {
                    Row row = s3.createRow(rr++);
                    row.createCell(0).setCellValue(it.getKnowledgeName());
                    row.createCell(1).setCellValue(it.getTotalCount() == null ? 0 : it.getTotalCount());
                    row.createCell(2).setCellValue(it.getCorrectCount() == null ? 0 : it.getCorrectCount());
                    row.createCell(3).setCellValue(fmt(it.getCorrectRate()) + "%");
                    for (int i = 0; i < 4; i++) {
                        row.getCell(i).setCellStyle(cellStyle);
                    }
                }
            }
            for (int i = 0; i < 4; i++) {
                s3.setColumnWidth(i, 24 * 256);
            }

            // ---------- Sheet4 题型正确率 ----------
            Sheet s4 = workbook.createSheet("题型正确率");
            writeDimensionSheet(s4, titleStyle, headerStyle, cellStyle,
                    "《" + vo.getExamName() + "》班级学情 - 题型正确率统计",
                    buildDimensionStats(dimExamId, dimClassId, "type").getItems(),
                    vo.getClassName(), vo.getCourseName(), vo.getFullScore());

            // ---------- Sheet5 难度正确率 ----------
            Sheet s5 = workbook.createSheet("难度正确率");
            writeDimensionSheet(s5, titleStyle, headerStyle, cellStyle,
                    "《" + vo.getExamName() + "》班级学情 - 难度正确率统计",
                    buildDimensionStats(dimExamId, dimClassId, "difficulty").getItems(),
                    vo.getClassName(), vo.getCourseName(), vo.getFullScore());

            // ---------- Sheet6 学生成绩明细 ----------
            Sheet s6 = workbook.createSheet("学生成绩明细");
            writeHeader(s6, headerStyle, "排名", "学号", "姓名", "得分", "试卷满分", "是否及格", "答题用时(分钟)", "提交时间", "状态");
            List<ClassStatsVO.StudentScoreItem> students = vo.getStudentScores();
            if (students != null) {
                int rr = 1;
                for (ClassStatsVO.StudentScoreItem it : students) {
                    Row row = s6.createRow(rr++);
                    if (it.getAbsent() != null && it.getAbsent()) {
                        row.createCell(0).setCellValue("—");
                        row.createCell(1).setCellValue(it.getUsername());
                        row.createCell(2).setCellValue(it.getRealName());
                        row.createCell(3).setCellValue("—");
                        row.createCell(4).setCellValue(fmt(vo.getFullScore()));
                        row.createCell(5).setCellValue("—");
                        row.createCell(6).setCellValue("—");
                        row.createCell(7).setCellValue("—");
                        row.createCell(8).setCellValue("缺考");
                    } else {
                        row.createCell(0).setCellValue(it.getRank() == null ? 0 : it.getRank().doubleValue());
                        row.createCell(1).setCellValue(it.getUsername());
                        row.createCell(2).setCellValue(it.getRealName());
                        row.createCell(3).setCellValue(fmt(it.getTotalScore()));
                        row.createCell(4).setCellValue(fmt(vo.getFullScore()));
                        row.createCell(5).setCellValue(it.getIsPass() != null && it.getIsPass() == 1 ? "及格" : "不及格");
                        row.createCell(6).setCellValue(it.getUsedMinutes() == null ? 0 : it.getUsedMinutes().doubleValue());
                        row.createCell(7).setCellValue(it.getSubmitTime() == null ? "" : it.getSubmitTime());
                        row.createCell(8).setCellValue("已交卷");
                    }
                    for (int i = 0; i < 9; i++) {
                        row.getCell(i).setCellStyle(cellStyle);
                    }
                }
            }
            int[] widths = {8, 16, 14, 10, 12, 12, 12, 18, 12};
            for (int i = 0; i < widths.length; i++) {
                s6.setColumnWidth(i, widths[i] * 256);
            }

            // ---------- 写出响应 ----------
            String safeClass = vo.getClassName() == null ? "" : vo.getClassName().replaceAll("[/\\\\:*?\"<>|]", "");
            String fileName = "班级学情统计_" + safeClass + "_" + System.currentTimeMillis() + ".xlsx";
            response.reset();
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
            workbook.write(response.getOutputStream());
            workbook.close();
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException ie) {
                throw ie;
            }
            throw new RuntimeException("导出班级学情 Excel 失败：" + e.getMessage(), e);
        }
    }

    // ==================== 私有工具 ====================

    private void checkTeacherExamAccess(Long examId, Long teacherId, Integer role) {
        if (role != null && role == 1) {
            if (teacherId == null || statsClassMapper.countOwnedExam(examId, teacherId) <= 0) {
                throw new IllegalArgumentException("无权查看非本人创建考试的学情数据");
            }
        }
    }

    /** 计算百分率（2 位小数），den 为 0 时返回 0 */
    private BigDecimal percent(int num, int den) {
        if (den <= 0) {
            return ZERO;
        }
        return new BigDecimal(num).multiply(ONE_HUNDRED)
                .divide(new BigDecimal(den), 2, RoundingMode.HALF_UP);
    }

    /** 答题用时（分钟，不足 1 分钟记 1 分钟） */
    private Integer calcMinutes(LocalDateTime start, LocalDateTime submit) {
        if (start == null || submit == null) {
            return null;
        }
        long minutes = Duration.between(start, submit).toMinutes();
        return Math.max(1, (int) minutes);
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FMT);
    }

    /** 数字格式化：去末尾 0 与小数点 */
    private String fmt(BigDecimal val) {
        if (val == null) {
            return "0";
        }
        String s = val.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    private String nvl(Integer v) {
        return v == null ? "0" : String.valueOf(v);
    }

    private CellStyle style(XSSFWorkbook wb, boolean bold, IndexedColors color, short size) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(bold);
        font.setFontHeightInPoints(size);
        if (color != null) {
            font.setColor(color.getIndex());
        }
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle, String... titles) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < titles.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(titles[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void createInfoRow(Sheet sheet, int rowIdx, String label1, String label2, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        Cell c0 = row.createCell(0);
        c0.setCellValue(label1);
        c0.setCellStyle(style);
        if (StringUtils.hasText(label2)) {
            Cell c1 = row.createCell(1);
            c1.setCellValue(label2);
            c1.setCellStyle(style);
        }
    }

    /** 题型名映射 */
    private String typeNameOf(Integer t) {
        if (t == null) return "未知";
        switch (t) {
            case 1: return "单选题";
            case 2: return "多选题";
            case 3: return "判断题";
            case 4: return "填空题";
            case 5: return "简答题";
            case 6: return "名词解释";
            default: return "题型" + t;
        }
    }

    /** 难度归档：1 简单，2 中等，3~5 困难 */
    private int difficultyBucket(Integer d) {
        if (d == null) return 0;
        if (d <= 1) return 1;
        if (d == 2) return 2;
        return 3;
    }

    /** 难度档位名 */
    private String difficultyNameOf(Integer bucket) {
        if (bucket == null) return "未知";
        switch (bucket) {
            case 1: return "简单";
            case 2: return "中等";
            case 3: return "困难";
            default: return "未知";
        }
    }

    private String nvlText(String v) {
        return v == null ? "" : v;
    }

    /** 写一张“作答维度统计”sheet（标题 + 筛选条件 + 表头 + 数据） */
    private void writeDimensionSheet(Sheet sheet, CellStyle titleStyle, CellStyle headerStyle, CellStyle cellStyle,
                                     String title, List<DimensionStatsVO.Item> items,
                                     String className, String courseName, BigDecimal fullScore) {
        Row t = sheet.createRow(0);
        t.createCell(0).setCellValue(title);
        t.getCell(0).setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

        Row info = sheet.createRow(1);
        info.createCell(0).setCellValue("筛选条件：课程 " + nvlText(courseName) + " / 班级 " + nvlText(className)
                + " / 试卷满分 " + fmt(fullScore) + " 分");
        info.getCell(0).setCellStyle(cellStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 3));

        Row header = sheet.createRow(2);
        String[] heads = {"维度", "总答题数", "答对题数", "正确率"};
        for (int i = 0; i < heads.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(heads[i]);
            c.setCellStyle(headerStyle);
        }

        int rr = 3;
        if (items != null) {
            for (DimensionStatsVO.Item it : items) {
                Row row = sheet.createRow(rr++);
                row.createCell(0).setCellValue(it.getName());
                row.createCell(1).setCellValue(it.getTotalCount() == null ? 0 : it.getTotalCount());
                row.createCell(2).setCellValue(it.getCorrectCount() == null ? 0 : it.getCorrectCount());
                row.createCell(3).setCellValue(fmt(it.getCorrectRate()) + "%");
                for (int i = 0; i < 4; i++) {
                    row.getCell(i).setCellStyle(cellStyle);
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            sheet.setColumnWidth(i, 22 * 256);
        }
    }
}
