package com.exam.exam_system.dto;

import lombok.Data;

/**
 * 高频错题统计项（从 exam_answer 实时聚合）
 */
@Data
public class HotWrongItem {

    private Long id;
    private String content;
    private Integer questionType;
    private Integer difficulty;
    private String options;
    private String answer;
    private String analysis;
    private Long courseId;
    private String courseName;

    /** 总作答次数 */
    private Long totalCount;

    /** 错误次数 */
    private Long wrongCount;

    /** 错误率（百分比，如 35.5 表示 35.5%） */
    private Double errorRate;
}
