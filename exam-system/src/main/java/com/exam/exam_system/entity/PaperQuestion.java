package com.exam.exam_system.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 试卷-题目关联实体，对应 paper_question 表
 *
 * <pre>
 * 字段对照（数据库 → 实体属性，MyBatis 驼峰自动映射）：
 * id           → id          主键
 * paper_id     → paperId     试卷ID
 * question_id  → questionId  题目ID
 * score        → score       该题分值
 * sort_order   → sortOrder   题目顺序
 * </pre>
 */
@Data
public class PaperQuestion {

    private Long id;
    private Long paperId;
    private Long questionId;
    private BigDecimal score;

    /** 题目顺序（1、2、3...） */
    private Integer sortOrder;
}
