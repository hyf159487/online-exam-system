package com.exam.exam_system.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 试题质量统计 VO
 */
@Data
public class QuestionQualityVO {

    private Long questionId;
    private String content;          // 题干摘要
    private String fullContent;      // 完整题干
    private Integer questionType;   // 题型：1单选 2多选 3判断 4填空 5简答
    private String questionTypeName;
    private BigDecimal score;        // 试题满分
    private Integer totalCount;      // 作答人数
    private Integer correctCount;    // 答对人数
    private Integer wrongCount;      // 答错人数
    private BigDecimal correctRate;  // 正确率(%)
    private BigDecimal wrongRate;    // 错误率(%)
    private BigDecimal difficulty;   // 难度系数 P = 平均分 / 满分
    private String difficultyLevel;  // 难度等级：过难/偏难/适中/偏易/过易
    private String options;          // 选项JSON
    private String answer;           // 答案
    private String analysis;         // 解析

    // ========== 学生答题明细 ==========
    private List<AnswerDetailItem> answerDetails;

    @Data
    public static class AnswerDetailItem {
        private Long studentId;
        private String realName;
        private String username;
        private String userAnswer;
        private Integer isCorrect;      // 0错误 1正确
        private BigDecimal getScore;    // 得分
    }
}
