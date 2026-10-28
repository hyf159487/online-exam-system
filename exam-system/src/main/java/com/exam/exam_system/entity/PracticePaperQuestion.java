package com.exam.exam_system.entity;

import lombok.Data;

/**
 * 练习卷-试题关联，对应 practice_paper_question 表
 */
@Data
public class PracticePaperQuestion {

    private Long id;

    /** 练习卷ID */
    private Long practicePaperId;

    /** 试题ID */
    private Long questionId;

    /** 题目排序号（从1开始） */
    private Integer sortNo;

    /** 单题分值 */
    private Integer score;

    // ===== 非表字段：预览/显示用 =====

    /** 题干（联表查询） */
    private String questionContent;

    /** 题型 */
    private Integer questionType;

    /** 难度 */
    private Integer difficulty;

    /** 正确答案 */
    private String correctAnswer;

    /** 解析 */
    private String analysis;

    /** 选项 */
    private String options;
}
