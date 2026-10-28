package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.OperationLogQuery;
import com.exam.exam_system.entity.OperationLog;
import com.exam.exam_system.mapper.OperationLogMapper;
import com.exam.exam_system.service.OperationLogService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * 操作日志服务实现
 *
 * <p>用 POI 生成 .xlsx 导出文件，复用项目已有的 POI 依赖。
 * 使用 @Async 异步记录日志（需主类开启 @EnableAsync，见 OperationLogAspect 配置）。
 */
@Service
@Slf4j
public class OperationLogServiceImpl implements OperationLogService {

    @Autowired
    private OperationLogMapper operationLogMapper;

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 操作模块下拉选项（与前端字典一致） */
    private static final List<String> MODULES = Arrays.asList(
            "题库管理", "试卷管理", "考试管理", "成绩管理",
            "账号管理", "班级管理", "课程管理",
            "试题审核管理", "系统管理", "自主练习管理", "消息通知"
    );

    /** 操作类型下拉选项（与前端字典一致） */
    private static final List<String> OPERATION_TYPES = Arrays.asList(
            "新增", "修改", "删除", "批量删除", "批量新增",
            "导入", "导出", "发布考试", "终止考试",
            "重置密码", "权限变更", "审核通过", "审核驳回"
    );

    @Override
    public PageResult<OperationLog> page(OperationLogQuery query) {
        long total = operationLogMapper.countByQuery(query);
        List<OperationLog> records = operationLogMapper.listByQuery(query);
        Long pn = query.getPageNum() == null ? 1L : query.getPageNum().longValue();
        Long ps = query.getPageSize() == null ? 10L : query.getPageSize().longValue();
        return PageResult.of(records, total, pn, ps);
    }

    @Override
    public OperationLog getById(Long id) {
        return operationLogMapper.selectById(id);
    }

    @Override
    public void record(OperationLog operationLog) {
        try {
            operationLogMapper.insert(operationLog);
        } catch (Exception e) {
            // 审计日志记录失败不能影响业务调用本身，仅打印日志
            // 注意：变量名不能用 log，会和 @Slf4j 生成的静态 log 字段冲突
            log.error("操作日志记录失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public void export(OperationLogQuery query, HttpServletResponse response) {
        // 导出时强制拉全部（限制 10000 条，超过截断）
        query.setPageNum(1);
        query.setPageSize(10000);
        List<OperationLog> list = operationLogMapper.listForExport(query);

        String fileName = "operation_log_" +
                java.time.LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded);

        // 表头：与前端表格列一致
        String[] headers = {
                "操作时间", "操作人", "操作模块", "操作类型",
                "操作描述", "业务ID", "IP地址", "状态",
                "请求地址", "请求方式", "异常信息", "耗时(ms)"
        };

        try (Workbook wb = new XSSFWorkbook();
             OutputStream out = response.getOutputStream()) {

            Sheet sheet = wb.createSheet("操作日志");
            // 表头样式：居中、加粗、灰底
            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }
            // 列宽自适应兜底
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 18 * 256);
            }

            CellStyle cellStyle = wb.createCellStyle();
            cellStyle.setBorderBottom(BorderStyle.THIN);
            cellStyle.setBorderTop(BorderStyle.THIN);
            cellStyle.setBorderLeft(BorderStyle.THIN);
            cellStyle.setBorderRight(BorderStyle.THIN);
            cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            int rowIdx = 1;
            for (OperationLog l : list) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(
                        l.getCreateTime() == null ? "" : l.getCreateTime().format(DT_FMT));
                row.createCell(col++).setCellValue(safe(l.getOperatorName()));
                row.createCell(col++).setCellValue(safe(l.getModule()));
                row.createCell(col++).setCellValue(safe(l.getOperationType()));
                row.createCell(col++).setCellValue(safe(l.getDescription()));
                row.createCell(col++).setCellValue(safe(l.getBusinessId()));
                row.createCell(col++).setCellValue(safe(l.getClientIp()));
                row.createCell(col++).setCellValue(
                        l.getStatus() != null && l.getStatus() == 1 ? "成功" : "失败");
                row.createCell(col++).setCellValue(safe(l.getRequestUrl()));
                row.createCell(col++).setCellValue(safe(l.getRequestMethod()));
                row.createCell(col++).setCellValue(safe(l.getErrorMsg()));
                row.createCell(col).setCellValue(
                        l.getCostMs() == null ? "" : String.valueOf(l.getCostMs()));
                for (int i = 0; i < headers.length; i++) {
                    row.getCell(i).setCellStyle(cellStyle);
                }
            }

            wb.write(out);
            out.flush();
        } catch (IOException e) {
            log.error("操作日志导出失败: {}", e.getMessage(), e);
            throw new RuntimeException("导出失败：" + e.getMessage(), e);
        }
    }

    @Override
    public List<String> listModules() {
        return MODULES;
    }

    @Override
    public List<String> listOperationTypes() {
        return OPERATION_TYPES;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
