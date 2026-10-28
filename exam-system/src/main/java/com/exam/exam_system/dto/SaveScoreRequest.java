package com.exam.exam_system.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 主观题阅卷-保存得分请求
 */
@Data
public class SaveScoreRequest {

    /** 答题明细ID（exam_answer.id） */
    private Long answerId;

    /** 教师给的主观题得分 */
    private BigDecimal reviewScore;

    /** 阅卷评语 */
    private String reviewOpinion;
}
