package com.exam.exam_system.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 教师提交判分请求 DTO
 *
 * <p>只包含简答题（题型5）的人工打分；客观题已由系统自动判分，不在此列。
 */
@Data
public class ReviewSubmitRequest {

    private List<Item> items;

    @Data
    public static class Item {
        /** 题目ID */
        private Long questionId;
        /** 教师给分（0 ~ 该题分值） */
        private BigDecimal score;
        /** 教师评语（可空） */
        private String comment;
    }
}
