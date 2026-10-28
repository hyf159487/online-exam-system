package com.exam.exam_system.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 学生端试卷里的题目 DTO（answer/analysis 绝不下发，防止作弊）
 */
@Data
public class StudentExamQuestion {

    private Long questionId;

    /** 题干 */
    private String content;

    /** 题型：1单选 2多选 3判断 4填空 5简答 */
    private Integer questionType;

    /** 选项JSON（仅单选/多选） */
    private String options;

    /** 该题分值 */
    private BigDecimal score;

    /** 题目顺序 */
    private Integer sortOrder;
}
