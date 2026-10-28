package com.exam.exam_system.service.impl;

import com.exam.exam_system.dto.*;
import com.exam.exam_system.entity.Exam;
import com.exam.exam_system.entity.ExamRecord;
import com.exam.exam_system.entity.SysClass;
import com.exam.exam_system.entity.SysUser;
import com.exam.exam_system.mapper.*;
import com.exam.exam_system.service.StatsExportService;
import com.exam.exam_system.service.StatsQuestionService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 学情报表导出 Service 实现
 *
 * <p>三张报表的「数据构建」与「写 Excel」是分开的两步：
 * <ul>
 *     <li>{@code buildXxxReport()} 只产出 {@link ReportPreviewVO}（列定义 + 数据行）；</li>
 *     <li>导出走 {@code writeReportSheet()} 写成 Excel，预览接口直接返回该 VO。</li>
 * </ul>
 * 这样页面预览看到的内容与导出的文件内容天然一致。</p>
 */
@Service
public class StatsExportServiceImpl implements StatsExportService {

    @Autowired
    private ExamMapper examMapper;

    @Autowired
    private SysClassMapper classMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private ExamRecordMapper examRecordMapper;

    @Autowired
    private StatsClassMapper statsClassMapper;

    @Autowired
    private StatsQuestionService statsQuestionService;

    /**
     * 报表里的时间统一用此格式。
     *
     * <p>注意：实体里的 startTime / submitTime 是 {@link LocalDateTime}，
     * 不能用 {@code SimpleDateFormat} 格式化（会抛
     * "Cannot format given Object as a Date"），必须用 DateTimeFormatter。</p>
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ==================== 导出入口 ====================

    @Override
    public void exportStudentScoreReport(Long examId, Long classId, Long teacherId, Integer role, HttpServletResponse response) {
        exportToExcel(response, buildStudentScoreReport(examId, classId), "学生成绩报表", examId);
    }

    @Override
    public void exportClassStatsReport(Long examId, Long classId, Long teacherId, Integer role, HttpServletResponse response) {
        exportToExcel(response, buildClassStatsReport(examId, classId), "班级学情统计报表", examId);
    }

    @Override
    public void exportQuestionQualityReport(Long examId, Long teacherId, Integer role, HttpServletResponse response) {
        exportToExcel(response, buildQuestionQualityReport(examId), "试题质量报表", examId);
    }

    @Override
    public ReportPreviewVO previewReport(Long examId, Long classId, Integer reportType) {
        if (examId == null) {
            throw new RuntimeException("请先选择考试");
        }
        if (reportType == null) {
            throw new RuntimeException("请先选择报表类型");
        }
        switch (reportType) {
            case 1:
                return buildStudentScoreReport(examId, classId);
            case 2:
                return buildClassStatsReport(examId, classId);
            case 3:
                return buildQuestionQualityReport(examId);
            default:
                throw new RuntimeException("不支持的报表类型");
        }
    }

    // ==================== 报表数据构建（导出与预览共用） ====================

    /**
     * 学生成绩报表：一名学生一行（只统计已交卷/已判分的记录）
     */
    private ReportPreviewVO buildStudentScoreReport(Long examId, Long classId) {
        Exam exam = requireExam(examId);

        ReportPreviewVO data = new ReportPreviewVO();
        data.setTitle("学生成绩报表 - " + exam.getExamName());
        data.addColumn("index", "序号", 70)
                .addColumn("studentName", "姓名", 110)
                .addColumn("studentNo", "学号", 140)
                .addColumn("className", "班级", 140)
                .addColumn("totalScore", "总分", 90)
                .addColumn("rank", "排名", 80)
                .addColumn("status", "状态", 90)
                .addColumn("submitTime", "交卷时间", 180);

        List<ExamRecord> records = examRecordMapper.listByPaperId(exam.getPaperId());
        if (records == null) {
            records = new ArrayList<>();
        }

        // 学生 / 班级信息缓存：避免逐行查询时对同一班级重复查库
        Map<Long, SysUser> userCache = new HashMap<>();
        Map<Long, String> classNameCache = new HashMap<>();

        List<ExamRecord> list = new ArrayList<>();
        for (ExamRecord record : records) {
            SysUser user = userCache.computeIfAbsent(record.getUserId(), userMapper::selectById);
            if (classId != null) {
                if (user == null || !classId.equals(user.getClassId())) {
                    continue;
                }
            }
            list.add(record);
        }

        // 总分降序；同分按交卷时间升序（先交卷者靠前），没有交卷时间的排最后
        list.sort((a, b) -> {
            BigDecimal sa = nz(a.getTotalScore());
            BigDecimal sb = nz(b.getTotalScore());
            int cmp = sb.compareTo(sa);
            if (cmp != 0) {
                return cmp;
            }
            LocalDateTime ta = a.getSubmitTime();
            LocalDateTime tb = b.getSubmitTime();
            if (ta == null && tb == null) {
                return 0;
            }
            if (ta == null) {
                return 1;
            }
            if (tb == null) {
                return -1;
            }
            return ta.compareTo(tb);
        });

        int index = 0;
        int rank = 0;
        BigDecimal lastScore = null;
        for (ExamRecord record : list) {
            index++;
            SysUser user = userCache.get(record.getUserId());
            BigDecimal score = nz(record.getTotalScore());

            // 同分并列：分数变化时才刷新名次（1、2、2、4…）
            if (lastScore == null || score.compareTo(lastScore) != 0) {
                rank = index;
                lastScore = score;
            }

            String studentName = user != null && user.getRealName() != null
                    ? user.getRealName() : "学生" + record.getUserId();
            String studentNo = user != null && user.getUsername() != null
                    ? user.getUsername() : String.valueOf(record.getUserId());
            String className = "-";
            if (user != null && user.getClassId() != null) {
                final Long cid = user.getClassId();
                className = classNameCache.computeIfAbsent(cid, id -> {
                    SysClass cls = classMapper.selectById(id);
                    return cls != null && cls.getClassName() != null ? cls.getClassName() : "-";
                });
            }

            data.addRow(Arrays.<Object>asList(
                    index,
                    studentName,
                    studentNo,
                    className,
                    score,
                    rank,
                    getStatusText(record.getStatus()),
                    formatDateTime(record.getSubmitTime())));
        }
        return data;
    }

    /**
     * 班级学情统计报表：一个班级一行（参考人数、平均分、及格率、分数段分布）
     */
    private ReportPreviewVO buildClassStatsReport(Long examId, Long classId) {
        Exam exam = requireExam(examId);

        ReportPreviewVO data = new ReportPreviewVO();
        data.setTitle("班级学情统计报表 - " + exam.getExamName());
        data.addColumn("index", "序号", 70)
                .addColumn("className", "班级", 160)
                .addColumn("studentCount", "参考人数", 100)
                .addColumn("avgScore", "平均分", 90)
                .addColumn("maxScore", "最高分", 90)
                .addColumn("minScore", "最低分", 90)
                .addColumn("passCount", "及格人数", 100)
                .addColumn("passRate", "及格率", 90)
                .addColumn("range0To59", "0-59分", 90)
                .addColumn("range60To79", "60-79分", 90)
                .addColumn("range80To100", "80-100分", 100);

        List<ClassStatsQuery> classes = statsClassMapper.listClassesByExam(examId);
        if (classes == null) {
            classes = new ArrayList<>();
        }
        if (classId != null) {
            classes.removeIf(c -> c.getClassId() == null || !classId.equals(c.getClassId()));
        }

        int index = 0;
        for (ClassStatsQuery cls : classes) {
            index++;
            Long cid = cls.getClassId();

            int studentCount = statsClassMapper.countStudents(examId, cid);
            BigDecimal avgScore = nz(statsClassMapper.avgScore(examId, cid));
            BigDecimal maxScore = nz(statsClassMapper.maxScore(examId, cid));
            BigDecimal minScore = nz(statsClassMapper.minScore(examId, cid));
            int passCount = statsClassMapper.countPass(examId, cid);
            BigDecimal passRate = studentCount > 0
                    ? BigDecimal.valueOf(passCount * 100.0 / studentCount).setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            StatsClassMapper.ScoreRangeCounts ranges = statsClassMapper.countScoreRanges(examId, cid);

            data.addRow(Arrays.<Object>asList(
                    index,
                    cls.getClassName() != null ? cls.getClassName() : "-",
                    studentCount,
                    avgScore,
                    maxScore,
                    minScore,
                    passCount,
                    passRate + "%",
                    ranges != null && ranges.getRange0To59() != null ? ranges.getRange0To59() : 0,
                    ranges != null && ranges.getRange60To79() != null ? ranges.getRange60To79() : 0,
                    ranges != null && ranges.getRange80To100() != null ? ranges.getRange80To100() : 0));
        }
        return data;
    }

    /**
     * 试题质量报表：一道题一行（作答人数、正误率、难度系数）
     */
    private ReportPreviewVO buildQuestionQualityReport(Long examId) {
        Exam exam = requireExam(examId);

        ReportPreviewVO data = new ReportPreviewVO();
        data.setTitle("试题质量报表 - " + exam.getExamName());
        data.addColumn("index", "题号", 70)
                .addColumn("questionTypeName", "题型", 90)
                .addColumn("content", "题干摘要", 320)
                .addColumn("score", "满分", 80)
                .addColumn("totalCount", "作答人数", 90)
                .addColumn("correctCount", "答对人数", 90)
                .addColumn("wrongCount", "答错人数", 90)
                .addColumn("correctRate", "正确率", 90)
                .addColumn("wrongRate", "错误率", 90)
                .addColumn("difficulty", "难度系数", 100)
                .addColumn("difficultyLevel", "难度等级", 90);

        List<QuestionQualityVO> questionList = statsQuestionService.getQuestionQualityList(examId);
        if (questionList == null) {
            questionList = new ArrayList<>();
        }

        for (int i = 0; i < questionList.size(); i++) {
            QuestionQualityVO q = questionList.get(i);
            data.addRow(Arrays.<Object>asList(
                    i + 1,
                    safe(q.getQuestionTypeName()),
                    safe(q.getContent()),
                    nz(q.getScore()),
                    nzInt(q.getTotalCount()),
                    nzInt(q.getCorrectCount()),
                    nzInt(q.getWrongCount()),
                    q.getCorrectRate() != null ? q.getCorrectRate().doubleValue() + "%" : "0%",
                    q.getWrongRate() != null ? q.getWrongRate().doubleValue() + "%" : "0%",
                    q.getDifficulty() != null ? q.getDifficulty() : BigDecimal.ZERO,
                    safe(q.getDifficultyLevel())));
        }
        return data;
    }

    // ==================== Excel 写出 ====================

    /**
     * 把报表数据写成 xlsx 并写入响应流（文件名：报表名_考试名.xlsx）
     */
    private void exportToExcel(HttpServletResponse response, ReportPreviewVO data,
                               String reportName, Long examId) {
        try (Workbook workbook = new XSSFWorkbook()) {
            writeReportSheet(workbook, data, reportName);
            writeExcel(response, workbook, fileBaseName(reportName, examId));
        } catch (Exception e) {
            throw new RuntimeException("导出失败：" + e.getMessage());
        }
    }

    /**
     * 通用写表：第 1 行标题（跨列合并）、第 2 行空行、第 3 行表头、其后为数据行
     */
    private void writeReportSheet(Workbook workbook, ReportPreviewVO data, String sheetName) {
        Sheet sheet = workbook.createSheet(sheetName);
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);

        List<ReportPreviewVO.Column> columns = data.getColumns();
        int rowNum = 0;

        // 标题
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(data.getTitle());
        titleCell.setCellStyle(titleStyle);
        if (!columns.isEmpty()) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, columns.size() - 1));
        }

        rowNum++;   // 标题与表头之间留一空行

        // 表头
        Row headerRow = sheet.createRow(rowNum++);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns.get(i).getLabel());
            cell.setCellStyle(headerStyle);
        }

        // 数据行
        for (List<Object> rowData : data.getRows()) {
            Row row = sheet.createRow(rowNum++);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = row.createCell(i);
                setCellValue(cell, i < rowData.size() ? rowData.get(i) : null);
                cell.setCellStyle(dataStyle);
            }
        }

        // 列宽（VO 里的 width 是 px，POI 单位是 1/256 字符宽）
        for (int i = 0; i < columns.size(); i++) {
            Integer width = columns.get(i).getWidth();
            sheet.setColumnWidth(i, (width != null ? width : 110) * 36);
        }
    }

    /** 数字写数字、其余写文本，避免 Excel 里出现 "123" 这类文本型数字 */
    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private void writeExcel(HttpServletResponse response, Workbook workbook, String fileName) throws Exception {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + ".xlsx");

        try (OutputStream os = response.getOutputStream()) {
            workbook.write(os);
            os.flush();
        }
    }

    // ==================== 工具方法 ====================

    /** 考试必须存在，否则后续查询都会静默返回空数据 */
    private Exam requireExam(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new RuntimeException("考试不存在，无法生成报表");
        }
        return exam;
    }

    private String fileBaseName(String reportName, Long examId) {
        Exam exam = examMapper.selectById(examId);
        String examName = exam != null && exam.getExamName() != null ? exam.getExamName() : "";
        return reportName + "_" + examName;
    }

    /** LocalDateTime → 「yyyy-MM-dd HH:mm:ss」；空值显示 - */
    private String formatDateTime(LocalDateTime time) {
        return time != null ? time.format(DATE_TIME_FORMATTER) : "-";
    }

    private BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private int nzInt(Integer value) {
        return value != null ? value : 0;
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private String getStatusText(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "考试中";
            case 1: return "已交卷";
            case 2: return "已批改";
            default: return "未知";
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }
}
