package com.exam.exam_system.dto;

import lombok.Data;

/**
 * 学生答题详情 VO（查看单个学生的作答题目）
 */
@Data
public class StudentAnswerDetailVO {

    private Long recordId;
    private Long questionId;
    private String content;
    private Integer questionType;
    private String options;
    private String userAnswer;
    private String correctAnswer;
    /** 0=错误 1=正确 null=未判分 */
    private Integer isCorrect;
    private java.math.BigDecimal score;
    /** 是否已作答 */
    private Boolean answered;
    private Integer sortOrder;
}
