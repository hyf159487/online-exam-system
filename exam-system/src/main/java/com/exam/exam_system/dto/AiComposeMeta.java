package com.exam.exam_system.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 智能组卷的「可组卷资源概览」：供组卷弹窗展示题库家底、知识点题量与 AI 状态
 */
@Data
public class AiComposeMeta {

    private Long courseId;

    private String courseName;

    /** 该学科可用于组卷（审核通过）的题目总数 */
    private long totalQuestions;

    /** 是否已配置大模型 api-key */
    private boolean aiEnabled;

    /** 大模型名称（未配置时为空） */
    private String model;

    /** 各题型可用题量（含难度细分） */
    private List<TypeStat> typeStats = new ArrayList<>();

    /** 知识点及可用题量（供「重点知识点」多选） */
    private List<KnowledgeStat> knowledgeOptions = new ArrayList<>();

    /** 题型可用题量统计 */
    @Data
    public static class TypeStat {
        private Integer questionType;
        private String questionTypeText;
        /** 该题型可用题目总数 */
        private int total;
        /** 简单题数（difficulty ≤ 1） */
        private int easy;
        /** 中等题数（difficulty = 2） */
        private int medium;
        /** 困难题数（difficulty ≥ 3） */
        private int hard;
    }

    /** 知识点可用题量统计 */
    @Data
    public static class KnowledgeStat {
        private Long knowledgeId;
        private String knowledgeName;
        private int questionCount;
    }
}
