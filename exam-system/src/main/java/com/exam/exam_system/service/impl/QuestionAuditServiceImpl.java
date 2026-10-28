package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.BizException;
import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.QuestionQuery;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.entity.QuestionAuditLog;
import com.exam.exam_system.mapper.QuestionAuditLogMapper;
import com.exam.exam_system.mapper.QuestionMapper;
import com.exam.exam_system.service.QuestionAuditService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 试题审核服务实现
 *
 * <p>审核历史基于流水表 question_audit_log：
 * 每次通过/驳回/教师重新提交都会新增一条流水，旧记录永久保留、不可篡改。
 */
@Service
public class QuestionAuditServiceImpl implements QuestionAuditService {

    /** 单次批量审核上限 */
    private static final int BATCH_LIMIT = 100;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private QuestionAuditLogMapper auditLogMapper;

    @Override
    public PageResult<Question> waitList(QuestionQuery query) {
        // 该列表只承载两种状态：
        //   0 = 待审核（管理员待办 / 教师查看自己已提交待审的题目）
        //   2 = 已驳回（教师查看被驳回的题目并修改重提）
        // 其余取值（含空）一律归为待审核，避免前端越权查询其它状态
        Integer status = query.getAuditStatus();
        if (status == null || (status != 0 && status != 2)) {
            status = 0;
        }
        query.setAuditStatus(status);
        query.setSortBy("createTime_desc");
        long total = questionMapper.countByQuery(query);
        List<Question> records = questionMapper.listByQuery(query);
        if (status == 0) {
            // 题库查重标记只对待审核题目有意义（提示审核人「题库已有该题」，防止重复放行）
            markDuplicateInBank(records);
        }
        return PageResult.of(records, total, query.getPageNum().longValue(), query.getPageSize().longValue());
    }

    @Override
    public PageResult<QuestionAuditLog> historyList(QuestionQuery query) {
        int offset = query.getOffset();
        int pageSize = query.getPageSize() != null ? query.getPageSize() : 10;
        long total = auditLogMapper.countByFilter(
                query.getAuditStatus(), query.getQuestionType(), query.getDifficulty(),
                query.getCreatorId(), query.getAuditUserId(), query.getKeyword(),
                query.getStartTime(), query.getEndTime());
        var records = auditLogMapper.listByFilter(
                query.getAuditStatus(), query.getQuestionType(), query.getDifficulty(),
                query.getCreatorId(), query.getAuditUserId(), query.getKeyword(),
                query.getStartTime(), query.getEndTime(), offset, pageSize);
        return PageResult.of(records, total, query.getPageNum().longValue(), (long) pageSize);
    }

    @Override
    public Map<String, Long> stats() {
        long total = 0;
        long passCount = 0;
        long rejectCount = 0;
        long resubmitCount = 0;
        for (Map<String, Object> row : auditLogMapper.countByAction()) {
            int action = ((Number) row.get("action")).intValue();
            long cnt = ((Number) row.get("cnt")).longValue();
            switch (action) {
                case QuestionAuditLog.ACTION_PASS -> passCount = cnt;
                case QuestionAuditLog.ACTION_REJECT -> rejectCount = cnt;
                case QuestionAuditLog.ACTION_RESUBMIT -> resubmitCount = cnt;
            }
        }
        total = passCount + rejectCount;
        return Map.of(
                "total", total,
                "passCount", passCount,
                "rejectCount", rejectCount,
                "resubmitCount", resubmitCount);
    }

    @Override
    public List<QuestionAuditLog> listLogsByQuestionId(Long questionId) {
        return auditLogMapper.listByQuestionId(questionId);
    }

    @Override
    public void pass(Long questionId, Long auditorId) {
        Question q = requirePending(questionId);
        List<Long> duplicateIds = findDuplicateIds(q);
        if (!duplicateIds.isEmpty()) {
            throw new BizException(buildDuplicateMsg(duplicateIds),
                    Map.of("duplicate", true, "duplicateIds", duplicateIds));
        }
        questionMapper.pass(questionId, auditorId);
        insertLog(questionId, QuestionAuditLog.ACTION_PASS, null, q, auditorId);
    }

    @Override
    public void reject(Long questionId, String opinion, Long auditorId) {
        Question q = requirePending(questionId);
        validateOpinion(opinion);
        questionMapper.reject(questionId, opinion, auditorId);
        insertLog(questionId, QuestionAuditLog.ACTION_REJECT, opinion, q, auditorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchPass(List<Long> questionIds, Long auditorId) {
        List<Long> ids = validateBatch(questionIds);
        List<Long> duplicateIds = new ArrayList<>();
        int count = 0;
        for (Long id : ids) {
            Question q = requirePending(id);
            List<Long> dups = findDuplicateIds(q);
            if (!dups.isEmpty()) {
                duplicateIds.addAll(dups);
                continue; // 题库已有该题：跳过该题，不通过
            }
            questionMapper.pass(id, auditorId);
            insertLog(id, QuestionAuditLog.ACTION_PASS, null, q, auditorId);
            count++;
        }
        if (!duplicateIds.isEmpty()) {
            throw new BizException(buildDuplicateMsg(duplicateIds),
                    Map.of("duplicate", true, "duplicateIds", duplicateIds));
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchReject(List<Long> questionIds, String opinion, Long auditorId) {
        List<Long> ids = validateBatch(questionIds);
        validateOpinion(opinion);
        int count = 0;
        for (Long id : ids) {
            Question q = requirePending(id);
            questionMapper.reject(id, opinion, auditorId);
            insertLog(id, QuestionAuditLog.ACTION_REJECT, opinion, q, auditorId);
            count++;
        }
        return count;
    }

    /**
     * 教师重新提交审核（由 QuestionController.resubmit 调用）
     * 同一事务内：状态重置 + 写入重提流水，保证原子性
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(Long questionId, Long submitterId) {
        questionMapper.resubmit(questionId);
        Question q = questionMapper.selectById(questionId);
        insertLog(questionId, QuestionAuditLog.ACTION_RESUBMIT, null, q, null);
    }

    @Override
    public void exportHistory(QuestionQuery query, HttpServletResponse response) {
        // 导出当前筛选条件下全部流水记录（不分页）
        var records = auditLogMapper.listByFilter(
                query.getAuditStatus(), query.getQuestionType(), query.getDifficulty(),
                query.getCreatorId(), query.getAuditUserId(), query.getKeyword(),
                query.getStartTime(), query.getEndTime(), 0, Integer.MAX_VALUE - 1);

        String[] headers = {"题目ID", "题干", "题型", "难度", "所属课程", "提交教师", "提交时间",
                "审核结果", "驳回原因", "审核操作人", "审核时间"};

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("审核历史");

            // 表头样式
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 18 * 256);
            }
            sheet.setColumnWidth(1, 40 * 256);
            sheet.setColumnWidth(4, 16 * 256);
            sheet.setColumnWidth(8, 30 * 256);

            CellStyle cellStyle = wb.createCellStyle();
            cellStyle.setWrapText(true);

            int rowIdx = 1;
            for (QuestionAuditLog log : records) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(log.getQuestionId() == null ? "" : log.getQuestionId().toString());
                Cell contentCell = row.createCell(col++);
                contentCell.setCellValue(safe(log.getContentSnapshot()));
                contentCell.setCellStyle(cellStyle);
                row.createCell(col++).setCellValue(typeText(log.getQuestionType()));
                row.createCell(col++).setCellValue(difficultyText(log.getDifficulty()));
                row.createCell(col++).setCellValue(safe(log.getCourseName()));
                row.createCell(col++).setCellValue(safe(log.getSubmitterName()));
                row.createCell(col++).setCellValue(log.getCreateTime() == null ? "" : log.getCreateTime().format(TIME_FMT));
                row.createCell(col++).setCellValue(actionText(log.getAction()));
                row.createCell(col++).setCellValue(safe(log.getOpinion()));
                row.createCell(col++).setCellValue(safe(actionText(log.getAction()).equals("重新提交")
                        ? log.getSubmitterName() : log.getAuditorName()));
                row.createCell(col).setCellValue(log.getAuditTime() == null ? "" : log.getAuditTime().format(TIME_FMT));
            }

            String fileName = URLEncoder.encode("试题审核历史记录.xlsx", StandardCharsets.UTF_8);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
            wb.write(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new RuntimeException("导出审核历史失败：" + e.getMessage());
        }
    }

    // ===== 内部工具 =====

    /**
     * 写入审核流水（留痕，只插入不修改）
     *
     * @param questionId 题目ID
     * @param action     动作：1通过 2驳回 3重新提交
     * @param opinion    驳回原因（驳回时）
     * @param q          题目（用于题干快照、提交教师；可能为 null，如刚重提后查不到则跳过快照）
     * @param auditorId  审核操作人（重提时为 null）
     */
    private void insertLog(Long questionId, int action, String opinion, Question q, Long auditorId) {
        QuestionAuditLog log = new QuestionAuditLog();
        log.setQuestionId(questionId);
        log.setAction(action);
        log.setOpinion(action == QuestionAuditLog.ACTION_REJECT ? opinion : null);
        log.setContentSnapshot(q != null ? q.getContent() : null);
        log.setSubmitterId(q != null ? q.getCreatorId() : null);
        log.setAuditorId(auditorId);
        log.setAuditTime(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    /** 校验题目存在且为待审核状态 */
    private Question requirePending(Long questionId) {
        Question q = questionMapper.selectById(questionId);
        if (q == null) {
            throw new RuntimeException("题目不存在（id=" + questionId + "）");
        }
        if (q.getAuditStatus() == null || q.getAuditStatus() != 0) {
            throw new RuntimeException("题目（id=" + questionId + "）不是待审核状态，无法审核");
        }
        return q;
    }

    /**
     * 审核通过前置查重：同学科题库中已存在题干一致的正式题目（audit_status=1）。
     * 返回所有重复题库题目 id，供前端弹窗定位；无重复返回空列表
     */
    /**
     * 待审核列表逐条查重并回填 duplicateBankIds（逗号分隔），
     * 供审核页一眼识别"题库已有该题"的重复项
     */
    private void markDuplicateInBank(List<Question> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (Question q : records) {
            List<Long> dups = findDuplicateIds(q);
            if (!dups.isEmpty()) {
                q.setDuplicateBankIds(dups.stream().map(String::valueOf).collect(Collectors.joining(",")));
            }
        }
    }

    private List<Long> findDuplicateIds(Question q) {
        if (q == null || q.getCourseId() == null || q.getContent() == null || q.getContent().isBlank()) {
            return List.of();
        }
        return questionMapper.listDuplicateIdsInBank(q.getCourseId(), q.getContent(), q.getId());
    }

    /** 组装"通过失败，题库已有该题"提示文案 */
    private String buildDuplicateMsg(List<Long> duplicateIds) {
        String ids = duplicateIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("、"));
        return "通过失败，题库已有该题：本学科下已存在题干相同的已通过题目（id=" + ids + "），请勿重复审核";
    }

    /** 校验驳回意见 */
    private void validateOpinion(String opinion) {
        if (opinion == null || opinion.isBlank()) {
            throw new RuntimeException("驳回原因不能为空");
        }
        if (opinion.length() > 500) {
            throw new RuntimeException("驳回原因不能超过500字");
        }
    }

    /** 校验批量参数 */
    private List<Long> validateBatch(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            throw new RuntimeException("请先勾选要审核的题目");
        }
        if (questionIds.size() > BATCH_LIMIT) {
            throw new RuntimeException("单次批量审核不能超过" + BATCH_LIMIT + "条");
        }
        return questionIds;
    }

    private String actionText(Integer action) {
        if (action == null) return "-";
        return switch (action) {
            case QuestionAuditLog.ACTION_PASS -> "已通过";
            case QuestionAuditLog.ACTION_REJECT -> "已驳回";
            case QuestionAuditLog.ACTION_RESUBMIT -> "重新提交";
            default -> "未知";
        };
    }

    private String typeText(Integer t) {
        if (t == null) return "-";
        return switch (t) {
            case 1 -> "单选题";
            case 2 -> "多选题";
            case 3 -> "判断题";
            case 4 -> "填空题";
            case 5 -> "简答题";
            case 6 -> "名词解释";
            default -> "未知";
        };
    }

    private String difficultyText(Integer d) {
        if (d == null) return "-";
        return switch (d) {
            case 1 -> "简单";
            case 2 -> "中等";
            case 3 -> "困难";
            case 4 -> "较难";
            case 5 -> "困难";
            default -> "-";
        };
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
