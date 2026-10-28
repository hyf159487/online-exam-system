package com.exam.exam_system.dto;

import lombok.Data;

import java.util.List;

/**
 * 学生自由刷题抽题条件
 *
 * <p>学科必选，其余均为可选过滤项：
 * <ul>
 *   <li>questionTypes：题型多选（1单选 2多选 3判断 4填空 5简答），空=不限制</li>
 *   <li>difficulties：难度值多选（1~5），空=不限制</li>
 *   <li>knowledgeId：知识点（可选，只刷该知识点题目）</li>
 *   <li>count：随机抽取题量（5~50，默认 10）</li>
 * </ul>
 */
@Data
public class PracticeQuestionQuery {

    /** 学科 ID（必选） */
    private Long courseId;

    /** 题型多选，空=全部 */
    private List<Integer> questionTypes;

    /** 难度值多选（1~5），空=全部 */
    private List<Integer> difficulties;

    /** 知识点 ID（可选） */
    private Long knowledgeId;

    /** 随机抽取题量（5~50，默认 10） */
    private Integer count;

    /**
     * 出题来源：normal=题库随机（默认）/ wrong=我的错题 / favorite=我的收藏。
     *
     * <p>用于「我的错题 / 我的收藏」页的批量练习入口：从这两个集合里随机抽题，
     * 而不是从整个题库抽题；此时 courseId 允许为空（错题、收藏往往跨学科）。</p>
     */
    private String source;

    /** 仅 source=wrong 生效：错误次数下限（含） */
    private Integer wrongCountMin;

    /** 仅 source=wrong 生效：错误次数上限（含） */
    private Integer wrongCountMax;
}
