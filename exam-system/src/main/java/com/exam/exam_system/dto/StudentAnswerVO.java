package com.exam.exam_system.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学生单次考试逐题作答详情 VO（班级学情 - 查看作答详情弹窗）
 */
@Data
public class StudentAnswerVO {

    private Long studentId;
    private String realName;
    private String username;

    private BigDecimal totalScore;   // 总分（可能为 null）
    private BigDecimal fullScore;    // 试卷满分

    private String submitTime;       // 提交时间
    private Integer usedMinutes;     // 答题用时（分钟）
    private Integer recordStatus;    // exam_record.status：1=已交卷待阅卷 2=已判分

    private Integer questionCount;   // 试卷总题数
    private Integer correctCount;    // 答对题数
    private Integer wrongCount;      // 答错题数
    private Integer pendingCount;    // 待人工阅卷题数（简答题未判分）

    private List<AnswerItem> answers;

    @Data
    public static class AnswerItem {
        private Integer sortOrder;      // 题号（1 起）
        private Long questionId;
        private Integer questionType;   // 1单选 2多选 3判断 4填空 5简答
        private String content;         // 题干
        private String options;         // 选项 JSON（单选/多选）
        private String standardAnswer;  // 标准答案
        private String analysis;        // 解析
        private String knowledgeName;   // 知识点
        private BigDecimal questionScore; // 本题分值
        private String userAnswer;      // 学生作答（null=未作答）
        private BigDecimal getScore;    // 本题得分
        private Integer isCorrect;      // 1对 0错 null=未判
        private Boolean correct;        // 计算后的判分结果：true对 false错 null待判/未作答
    }
}
