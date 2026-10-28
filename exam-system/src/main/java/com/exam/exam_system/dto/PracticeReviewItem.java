package com.exam.exam_system.dto;

import lombok.Data;

/**
 * 批改作业：单道主观题的批改提交项
 */
@Data
public class PracticeReviewItem {

    /** 作答明细ID（practice_answer.id） */
    private Long answerId;

    /** 教师给的得分（0 ~ 该题满分） */
    private Integer reviewScore;

    /** 评语 */
    private String reviewOpinion;
}
