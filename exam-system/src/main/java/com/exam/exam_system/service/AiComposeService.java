package com.exam.exam_system.service;

import com.exam.exam_system.dto.AiComposeMeta;
import com.exam.exam_system.dto.AiComposePlan;
import com.exam.exam_system.dto.AiComposeRequest;

/**
 * AI 智能组卷服务
 *
 * <p>教师只需给出「目标总分 + 难度倾向 + 重点知识点 + 一句话要求」，
 * 系统自动生成组卷方案（题型配比、难度分布、分值分配）并从题库抽题，覆盖式写入试卷。
 *
 * <ul>
 *     <li>已配置 ai.api-key：由大模型生成组卷方案（qwen / deepseek 等 OpenAI 兼容模型）；</li>
 *     <li>未配置 api-key 或调用失败：降级为本地规则引擎（题型配比 + 难度分布 + 题库可用量自适应），
 *         保证离线环境功能依然可用可演示。</li>
 * </ul>
 *
 * <p>两条通道抽题时都会：优先命中教师指定的重点知识点、按「知识点覆盖率最大化」择优、
 * 某难度档题量不足时自动顺延到相邻难度并给出提示。
 */
public interface AiComposeService {

    /**
     * 组卷资源概览：题库题型/难度可用题量、知识点题量、大模型启用状态
     *
     * @param paperId 试卷 ID（取试卷所属学科）
     */
    AiComposeMeta meta(Long paperId);

    /**
     * AI 智能组卷（覆盖式）：生成方案 → 自动抽题 → 保存为试卷题目
     *
     * @param paperId 试卷 ID
     * @param request 组卷要求（目标总分、难度倾向、重点知识点、自然语言补充要求）
     * @return 组卷方案 + 实际抽取的题目清单 + 调整提示
     */
    AiComposePlan compose(Long paperId, AiComposeRequest request);
}
