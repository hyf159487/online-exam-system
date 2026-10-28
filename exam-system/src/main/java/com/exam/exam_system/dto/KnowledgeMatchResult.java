package com.exam.exam_system.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 知识点匹配结果（按输入题目顺序返回）
 *
 * <p>AI/本地规则可为一题推荐 1~3 个知识点：knowledgeIds 为完整推荐集合，
 * 其中第一个（主知识点）同时兼容回填到 knowledgeId / knowledgeName 单值字段。
 * 若某题没有匹配到合适知识点，knowledgeIds 为空列表，单值字段为 null。
 */
@Data
public class KnowledgeMatchResult {

    /** 题目 ID */
    private Long questionId;

    /** 推荐的完整知识点 ID 集合（1~3 个；可为空） */
    private List<Long> knowledgeIds;

    /** 推荐的完整知识点名称集合（与 knowledgeIds 一一对应） */
    private List<String> knowledgeNames;

    /** 主知识点 ID（= knowledgeIds 第一个，兼容旧单值使用；null = 未匹配到） */
    private Long knowledgeId;

    /** 主知识点名称（= knowledgeNames 第一个，兼容旧单值使用） */
    private String knowledgeName;

    /** 匹配理由（一句话说明） */
    private String reason;

    /** 匹配来源：llm=大模型 / local=本地规则 */
    private String source;
}
