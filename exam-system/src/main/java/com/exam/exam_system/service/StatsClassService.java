package com.exam.exam_system.service;

import com.exam.exam_system.dto.*;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * 班级学情统计 Service
 */
public interface StatsClassService {

    /**
     * 获取可统计的考试列表
     */
    List<StatsExamVO> getExamList(Long teacherId, Integer role);

    /**
     * 获取某考试下可统计的班级列表
     */
    List<ClassStatsQuery> getClassList(Long examId, Long teacherId, Integer role);

    /**
     * 获取班级学情统计数据（动态基于试卷满分计算及格/优秀/分数段）
     */
    ClassStatsVO getClassStats(ClassStatsQuery query);

    /**
     * 查看某学生在该考试下的逐题作答详情
     */
    StudentAnswerVO getStudentAnswerDetail(Long examId, Long studentId, Long teacherId, Integer role);

    /**
     * 获取“作答维度统计”（按题型 type / 按难度 difficulty）
     */
    DimensionStatsVO getDimensionStats(ClassStatsQuery query, String mode);

    /**
     * 导出班级学情 Excel 报表（指标 + 分数段 + 知识点 + 学生明细，首行记录试卷满分）
     */
    void exportClassStatsExcel(ClassStatsQuery query, HttpServletResponse response);
}
