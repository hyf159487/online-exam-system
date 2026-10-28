package com.exam.exam_system.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 班级学情“作答维度统计”VO（右侧图表：按题型 / 按难度 双模式）
 *
 * <p>统一返回：名称、总作答数、答对数、正确率。
 * mode = "type" 时 name 为题型名（单选/多选/判断/填空/简答）；
 * mode = "difficulty" 时 name 为难度档位（简单/中等/困难）。</p>
 */
@Data
public class DimensionStatsVO {

    /** 统计维度：type=按题型，difficulty=按难度 */
    private String dimension;

    /** 维度项列表（已按顺序排好） */
    private List<Item> items;

    @Data
    public static class Item {
        /** 原始 key（题型 1-5 或难度 1-5） */
        private Integer key;
        /** 展示名称（题型名 / 难度档位名） */
        private String name;
        /** 总作答数 */
        private Integer totalCount;
        /** 答对数 */
        private Integer correctCount;
        /** 正确率（百分比，2 位小数） */
        private BigDecimal correctRate;
    }
}
