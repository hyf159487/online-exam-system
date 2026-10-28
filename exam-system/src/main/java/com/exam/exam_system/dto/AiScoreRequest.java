package com.exam.exam_system.dto;

import lombok.Data;

import java.util.List;

/**
 * 主观题 AI 预评分请求
 *
 * <p>单题预评分用 answerId；整卷/多题预评分用 answerIds。
 */
@Data
public class AiScoreRequest {

    /** 答题明细ID（单题预评分） */
    private Long answerId;

    /** 答题明细ID列表（批量预评分，单次最多 10 题） */
    private List<Long> answerIds;
}
