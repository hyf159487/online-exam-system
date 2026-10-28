package com.exam.exam_system.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 组卷页"已选题目"联表查询结果 DTO
 * （paper_question JOIN question 后返回给前端的字段）
 */
@Data
public class PaperQuestionItem {

    /** 题目ID */
    private Long questionId;

    /** 题干 */
    private String content;

    /** 题型：1单选 2多选 3判断 4填空 5简答 */
    private Integer questionType;

    /** 所属学科ID（来自 question 表，错题归集时冗余记录，方便按学科筛选） */
    private Long courseId;

    /** 选项JSON（仅单选/多选） */
    private String options;

    /** 参考答案 */
    private String answer;

    /** 该题在本试卷中的分值 */
    private BigDecimal score;

    /** 题目顺序 */
    private Integer sortOrder;
}
