package com.exam.exam_system.service;

import com.exam.exam_system.dto.AiScoreResult;

import java.util.List;

/**
 * 主观题 AI 预评分 Service
 *
 * <p>为教师阅卷提供「机器预评分」：根据题干、参考答案（评分要点）、满分与学生作答，
 * 给出建议得分、学生可见评语、教师可见评分依据（命中/缺失得分点）与置信度。
 *
 * <p>两种评分通道：
 * <ul>
 *     <li>已配置 ai.api-key：调用 OpenAI 兼容 Chat Completions 接口，由大模型按评分要点打分；</li>
 *     <li>未配置 api-key 或调用失败：降级为本地规则引擎（得分点覆盖率 + 文字相似度 + 作答完整度加权），
 *         保证离线 / 无 key 环境下功能依然可用可演示。</li>
 * </ul>
 *
 * <p>预评分结果不落库，教师确认后再调用阅卷保存接口，避免 AI 直接改学生成绩。
 */
public interface AiScoreService {

    /**
     * 单题 AI 预评分
     *
     * @param answerId     答题明细ID
     * @param operatorId   操作人ID（教师只能评分自己创建考试的主观题）
     * @param operatorRole 操作人角色（1教师 2管理员）
     */
    AiScoreResult score(Long answerId, Long operatorId, Integer operatorRole);

    /**
     * 批量 AI 预评分（整卷待阅题一键预评）
     *
     * @param answerIds 答题明细ID列表，自动去重，单次最多 10 题
     */
    List<AiScoreResult> scoreBatch(List<Long> answerIds, Long operatorId, Integer operatorRole);
}
