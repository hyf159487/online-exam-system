package com.exam.exam_system.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 随机组卷请求 DTO
 *
 * <p>按"题型 + 难度 + 数量 + 每题分值"的规则从题库随机抽题，
 * 抽到的题目整体替换试卷原有题目（覆盖式）。
 */
@Data
public class RandomComposeRequest {

    /** 抽题规则列表 */
    private List<Rule> rules;

    @Data
    public static class Rule {
        /** 题型：1单选 2多选 3判断 4填空 5简答 */
        private Integer questionType;
        /** 难度：1简单 2中等 3困难（null = 不限难度） */
        private Integer difficulty;
        /** 抽题数量 */
        private Integer count;
        /** 每题分值（null = 用题目自带分值） */
        private BigDecimal score;
    }
}
