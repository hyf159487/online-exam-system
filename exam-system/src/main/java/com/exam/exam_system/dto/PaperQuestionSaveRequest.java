package com.exam.exam_system.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 保存组卷结果 DTO（前端提交）
 *
 * <pre>
 * {
 *   "paperId": 1,
 *   "items": [
 *     {"questionId": 10, "score": 2.0},
 *     {"questionId": 11, "score": 5.0}
 *   ]
 * }
 * sort_order 按 items 数组顺序自动生成（1、2、3...）
 * </pre>
 */
@Data
public class PaperQuestionSaveRequest {

    private Long paperId;

    private List<Item> items;

    @Data
    public static class Item {
        private Long questionId;
        private BigDecimal score;
    }
}
