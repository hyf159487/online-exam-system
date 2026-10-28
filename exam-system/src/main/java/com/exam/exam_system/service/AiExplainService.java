package com.exam.exam_system.service;

import com.exam.exam_system.dto.AiChatRequest;
import com.exam.exam_system.dto.AiExplainResult;

import java.util.List;

/**
 * AI 错题讲解服务
 *
 * <p>根据错题上下文（题干/选项/学生答案/正确答案/学科）生成针对性的讲解：
 * <ul>
 *     <li>配置了大模型 api-key：调用 OpenAI 兼容接口，返回大模型生成的 Markdown 讲解，并支持多轮追问；</li>
 *     <li>未配置 api-key：降级为本地规则解析引擎，按题型模板生成结构化讲解，保证功能可用（仅首轮）。</li>
 * </ul>
 */
public interface AiExplainService {

    /**
     * 对某道错题生成 AI 讲解（等价于用默认开场白发起一轮对话）
     *
     * @param userId         当前学生 ID（校验错题归属）
     * @param wrongQuestionId 错题 ID
     * @return 讲解结果（content 为 Markdown 格式）
     */
    AiExplainResult explain(Long userId, Long wrongQuestionId);

    /**
     * AI 错题多轮对话：携带历史上下文继续追问
     *
     * @param userId          当前学生 ID（校验错题归属）
     * @param wrongQuestionId 错题 ID
     * @param question        学生本次提问（空则使用默认开场白）
     * @param history         此前多轮 user/assistant 消息（可为空，最多保留最近 12 条）
     * @return 讲解结果（content 为 Markdown 格式）
     */
    AiExplainResult chat(Long userId, Long wrongQuestionId, String question, List<AiChatRequest.Message> history);

    /**
     * 按题库题目生成 AI 讲解（学生自由刷题「背题模式」用，等价于默认开场白的首轮对话）。
     * 无需该题先成为错题；题目须已审核通过。
     *
     * @param questionId  题目 ID
     * @param userAnswer  学生当前作答（可空，预习/背题场景无作答）
     * @return 讲解结果（content 为 Markdown 格式）
     */
    AiExplainResult explainQuestion(Long questionId, String userAnswer);

    /**
     * 按题库题目 AI 多轮对话（背题模式追问，userAnswer 可空）
     *
     * @param questionId  题目 ID
     * @param userAnswer  学生当前作答（可空）
     * @param question    学生本次提问（空则使用默认开场白）
     * @param history     此前多轮 user/assistant 消息（可为空，最多保留最近 12 条）
     * @return 讲解结果（content 为 Markdown 格式）
     */
    AiExplainResult chatQuestion(Long questionId, String userAnswer,
                                 String question, List<AiChatRequest.Message> history);
}
