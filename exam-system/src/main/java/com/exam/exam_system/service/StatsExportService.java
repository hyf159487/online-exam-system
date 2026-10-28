package com.exam.exam_system.service;

import com.exam.exam_system.dto.ReportPreviewVO;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 学情报表导出 Service
 */
public interface StatsExportService {

    /**
     * 导出学生成绩报表
     */
    void exportStudentScoreReport(Long examId, Long classId, Long teacherId, Integer role, HttpServletResponse response);

    /**
     * 导出班级学情统计报表
     */
    void exportClassStatsReport(Long examId, Long classId, Long teacherId, Integer role, HttpServletResponse response);

    /**
     * 导出试题质量报表
     */
    void exportQuestionQualityReport(Long examId, Long teacherId, Integer role, HttpServletResponse response);

    /**
     * 报表预览：返回与导出 Excel 完全一致的表头与数据（JSON）
     *
     * @param reportType 1 学生成绩 / 2 班级学情统计 / 3 试题质量
     */
    ReportPreviewVO previewReport(Long examId, Long classId, Integer reportType);
}
