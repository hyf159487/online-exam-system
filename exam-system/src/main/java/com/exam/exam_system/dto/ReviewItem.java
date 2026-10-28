package com.exam.exam_system.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 阅卷页单题信息 DTO
 *
 * <p>客观题展示标准答案/判定结果；简答题展示学生答案，由教师打分并写评语。
 */
@Data
public class ReviewItem {

    /** 题目ID */
    private Long questionId;

    /** 答题明细ID（exam_answer.id）：主观题 AI 预评分 / 保存得分使用，未作答时为 null */
    private Long answerId;

    /** 题型：1单选 2多选 3判断 4填空 5简答 */
    private Integer questionType;

    /** 题干 */
    private String content;

    /** 选项JSON（仅单选/多选） */
    private String options;

    /** 标准答案 */
    private String standardAnswer;

    /** 该题分值（来自 paper_question） */
    private BigDecimal score;

    /** 学生作答 */
    private String userAnswer;

    /** 是否答对：1对 0错（简答题判分后写回） */
    private Integer isCorrect;

    /** 客观题自动得分 / 简答题教师打分 */
    private BigDecimal getScore;

    /** 教师评语（简答题） */
    private String teacherComment;

    /** 是否已作答（交卷时未作答则为 false） */
    private Boolean answered;
}
