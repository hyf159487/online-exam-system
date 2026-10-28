package com.exam.exam_system.dto;

import lombok.Data;

/**
 * AI 错题讲解结果
 *
 * <p>source 区分讲解来源：
 * <ul>
 *     <li>llm   —— 真实大模型生成（已配置 api-key）</li>
 *     <li>local —— 本地规则解析引擎降级生成（未配置 api-key）</li>
 * </ul>
 * 前端可根据 source 展示不同的标识。
 */
@Data
public class AiExplainResult {

    /** 错题 ID（仅错题本讲解有值；试卷回看、背题模式等场景为 null） */
    private Long wrongQuestionId;

    /** 讲解内容（Markdown 格式，前端可直接渲染） */
    private String content;

    /** 来源：llm=大模型 / local=本地规则 */
    private String source;

    /** 使用的模型名（大模型时才有值） */
    private String model;
}
