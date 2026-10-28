package com.exam.exam_system.dto;

import lombok.Data;

/**
 * 成绩统计 DTO
 *
 * <p>分数段按「得分率」（得分 / 试卷满分 × 100）划分，
 * 这样满分 60 的卷子和满分 100 的卷子可以共用同一套分段。
 */
@Data
public class GradeStats {

    /** 参考人次 */
    private Long totalCount;

    /** 平均分（原始分） */
    private Double avgScore;

    /** 最高分 */
    private Double maxScore;

    /** 最低分 */
    private Double minScore;

    /** 及格人次 */
    private Long passCount;

    /** 得分率 < 60% 的人数 */
    private Long d0To59;

    /** 得分率 60%~69% 的人数 */
    private Long d60To69;

    /** 得分率 70%~79% 的人数 */
    private Long d70To79;

    /** 得分率 80%~89% 的人数 */
    private Long d80To89;

    /** 得分率 >= 90% 的人数 */
    private Long d90To100;
}
