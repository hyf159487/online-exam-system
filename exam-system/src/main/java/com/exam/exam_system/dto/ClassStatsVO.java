package com.exam.exam_system.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 班级学情统计数据 VO
 *
 * <p>试卷满分不固定（50 / 100 / 120 / 150 等），及格线 / 优秀线由后端按
 * 满分动态计算：及格 = 满分 × 0.6，优秀 = 满分 × 0.85；分数段统一按
 * “得分率百分比区间”输出，兼容所有满分规格的试卷。</p>
 */
@Data
public class ClassStatsVO {

    // ========== 背景信息 ==========
    private Long examId;
    private String examName;
    private String courseName;
    private String className;

    /** 试卷满分 */
    private BigDecimal fullScore;
    /** 及格线 = 满分 × 0.6 */
    private BigDecimal passLine;
    /** 优秀线 = 满分 × 0.85 */
    private BigDecimal excellentLine;

    // ========== 基础指标 ==========
    private Integer classSize;        // 班级总人数（全班在册，供班级下拉回显对照）
    private Integer courseSize;       // 课程人数（已加入本考试所属课程的学生数）
    private Integer candidateCount;   // 应考人数（本班已加入该课程、应参加本次考试的学生数）
    private Integer studentCount;     // 参考人数（有成绩记录）
    private Integer absentCount;      // 缺考人数（应考 - 参考）
    private BigDecimal avgScore;      // 平均分
    private BigDecimal maxScore;      // 最高分
    private BigDecimal minScore;      // 最低分
    private Integer passCount;        // 及格人数
    private Integer failCount;        // 不及格人数
    private Integer excellentCount;   // 优秀人数
    private BigDecimal passRate;      // 及格率（百分比，如 66.67 = 66.67%）
    private BigDecimal excellentRate; // 优秀率（百分比）

    // ========== 分数段分布（百分比分段，方案 A） ==========
    private List<ScoreRangeItem> scoreRanges;

    // ========== 知识点正确率 ==========
    private List<KnowledgeStatsItem> knowledgeStats;

    // ========== 学生成绩明细（含缺考，按分数降序） ==========
    private List<StudentScoreItem> studentScores;

    @Data
    public static class ScoreRangeItem {
        private String rangeName;       // 百分比区间，如 "0-59%"
        private Integer count;          // 该区间人数
        private BigDecimal ratio;       // 占参考人数百分比，如 66.67
        private String actualText;      // 该区间对应的实际分数描述，如 "0-88.5 分"
    }

    @Data
    public static class KnowledgeStatsItem {
        private Long knowledgeId;
        private String knowledgeName;
        private Integer totalCount;     // 该知识点总答题数
        private Integer correctCount;   // 答对数
        private BigDecimal correctRate; // 正确率（百分比，2 位小数）
    }

    @Data
    public static class StudentScoreItem {
        private Long studentId;
        private Long recordId;          // 参考记录 ID（未参考为 null，作答详情用）
        private String realName;
        private String username;
        private BigDecimal totalScore;  // 得分，null 表示缺考/无成绩
        private Boolean absent;         // true=缺考
        private Integer rank;           // 排名（缺考为 null）
        private Integer isPass;         // 1=及格 0=不及格（缺考为 null，基于动态及格线）
        private Integer usedMinutes;    // 答题用时（分钟）
        private String submitTime;      // 提交时间 yyyy-MM-dd HH:mm:ss
    }
}
