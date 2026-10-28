package com.exam.exam_system.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 智能组卷结果（组卷方案 + 实际抽取的题目清单）
 *
 * <p>source 区分方案来源：
 * <ul>
 *     <li>llm   —— 大模型生成组卷方案（已配置 ai.api-key）</li>
 *     <li>local —— 本地规则引擎生成（未配置 api-key 或大模型调用失败降级）</li>
 * </ul>
 */
@Data
public class AiComposePlan {

    /** 方案来源：llm=大模型 / local=本地规则引擎 */
    private String source;

    /** 使用的模型名（大模型时才有值） */
    private String model;

    /** 组卷思路说明（一句话说明本次方案的取舍与依据） */
    private String strategy;

    /** 目标总分 */
    private BigDecimal targetScore;

    /** 实际总分 */
    private BigDecimal totalScore;

    /** 实际题目数量 */
    private Integer questionCount;

    /** 实际使用的抽题规则（按题型/难度拆分，含题库不足时的实际题数） */
    private List<Rule> rules = new ArrayList<>();

    /** 实际难度分布 */
    private List<DifficultyStat> difficultyStats = new ArrayList<>();

    /** 提示信息：题库不足自动调整、重点知识点占比、降级原因等 */
    private List<String> warnings = new ArrayList<>();

    /** 抽到的题目清单（按题型、难度顺序排列） */
    private List<Item> items = new ArrayList<>();

    /** 单条抽题规则 */
    @Data
    public static class Rule {
        private Integer questionType;
        private String questionTypeText;
        /** 难度档：1简单 2中等 3困难 */
        private Integer difficulty;
        private String difficultyText;
        /** 计划题数 */
        private Integer count;
        /** 实际抽到的题数 */
        private Integer actualCount;
        /** 每题分值 */
        private BigDecimal score;

        public static Rule of(Integer questionType, Integer difficulty, Integer count,
                              Integer actualCount, BigDecimal score) {
            Rule r = new Rule();
            r.setQuestionType(questionType);
            r.setQuestionTypeText(typeText(questionType));
            r.setDifficulty(difficulty);
            r.setDifficultyText(difficultyText(difficulty));
            r.setCount(count);
            r.setActualCount(actualCount);
            r.setScore(score);
            return r;
        }
    }

    /** 难度分布统计 */
    @Data
    public static class DifficultyStat {
        private Integer difficulty;
        private String difficultyText;
        private Integer count;
        private BigDecimal score;

        public static DifficultyStat of(Integer difficulty, Integer count, BigDecimal score) {
            DifficultyStat s = new DifficultyStat();
            s.setDifficulty(difficulty);
            s.setDifficultyText(difficultyText(difficulty));
            s.setCount(count);
            s.setScore(score);
            return s;
        }
    }

    /** 题目清单项 */
    @Data
    public static class Item {
        private Long questionId;
        private Integer questionType;
        private String questionTypeText;
        private String content;
        private Integer difficulty;
        private String difficultyText;
        private BigDecimal score;
        private Long knowledgeId;
        private String knowledgeName;

        public static Item of(com.exam.exam_system.entity.Question q, BigDecimal score) {
            Item item = new Item();
            item.setQuestionId(q.getId());
            item.setQuestionType(q.getQuestionType());
            item.setQuestionTypeText(typeText(q.getQuestionType()));
            item.setContent(q.getContent());
            item.setDifficulty(q.getDifficulty());
            item.setDifficultyText(difficultyText(q.getDifficulty()));
            item.setScore(score);
            item.setKnowledgeId(q.getKnowledgeId());
            // 一题多知识点时优先展示全部名称，回退到主知识点
            item.setKnowledgeName(q.getKnowledgeNames() != null && !q.getKnowledgeNames().isBlank()
                    ? q.getKnowledgeNames() : q.getKnowledgeName());
            return item;
        }
    }

    // ==================== 文案工具 ====================

    /** 题型中文名 */
    public static String typeText(Integer type) {
        if (type == null) return "未知";
        return switch (type) {
            case 1 -> "单选题";
            case 2 -> "多选题";
            case 3 -> "判断题";
            case 4 -> "填空题";
            case 5 -> "简答题";
            case 6 -> "名词解释";
            default -> "未知";
        };
    }

    /** 难度档中文名 */
    public static String difficultyText(Integer d) {
        if (d == null) return "不限";
        return switch (d) {
            case 1 -> "简单";
            case 2 -> "中等";
            case 3 -> "困难";
            default -> "中等";
        };
    }
}
