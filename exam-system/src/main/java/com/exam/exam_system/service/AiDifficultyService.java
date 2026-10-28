package com.exam.exam_system.service;

import com.exam.exam_system.dto.AiDifficultyResult;
import com.exam.exam_system.entity.Question;

import java.util.List;

/**
 * AI 题目难度评估服务
 *
 * <p>根据题目内容（题干/选项/题型/解析/学科）智能判断题目的难度等级：
 * <ul>
 *     <li>配置了大模型 api-key：调用 OpenAI 兼容接口，返回大模型评估的难度；</li>
 *     <li>未配置 api-key：降级为本地规则引擎（基于题型、题干长度、关键词等启发式），
 *         保证离线 / 无 key 环境下功能依然可用。</li>
 * </ul>
 *
 * <p>难度等级：1=简单，2=中等，3=困难
 */
public interface AiDifficultyService {

    /**
     * 评估单道题目的难度
     *
     * @param question 题目数据（至少包含 questionType、content、options、answer、analysis、courseName）
     * @return 难度评估结果（difficulty: 1~3, source: llm/local）
     */
    AiDifficultyResult evaluate(Question question);

    /**
     * 批量评估多道题目的难度（内部会合并调用，减少网络开销）
     *
     * @param questions 题目列表
     * @return 与输入顺序一致的难度评估结果列表
     */
    List<AiDifficultyResult> evaluateBatch(List<Question> questions);
}
