package com.exam.exam_system.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 智能组卷请求 DTO
 *
 * <p>教师只需要给出「目标总分 + 难度倾向 + 重点知识点 + 一句话要求」，
 * 由大模型（未配置 api-key 时降级为本地规则引擎）生成组卷方案并自动抽题，
 * 抽到的题目整体替换试卷原有题目（覆盖式）。
 */
@Data
public class AiComposeRequest {

    /** 目标总分（空 = 100） */
    private BigDecimal totalScore;

    /** 难度倾向：1 偏基础 / 2 均衡 / 3 偏挑战（空 = 2 均衡） */
    private Integer difficultyPreference;

    /** 重点考察的知识点 id（空 = 按知识点覆盖率自动均衡分布） */
    private List<Long> knowledgeIds;

    /** 教师的自然语言补充要求，如"侧重第三章，计算与综合应用题多一点" */
    private String requirement;

    /** 限定可用题型（空 = 系统按题库实际情况自选，取值 1单选 2多选 3判断 4填空 5简答 6名词解释） */
    private List<Integer> questionTypes;
}
