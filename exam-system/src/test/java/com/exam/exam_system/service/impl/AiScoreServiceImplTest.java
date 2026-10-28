package com.exam.exam_system.service.impl;

import com.exam.exam_system.dto.AiScoreResult;
import com.exam.exam_system.entity.ExamAnswer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主观题 AI 预评分 —— 本地规则引擎单元测试
 *
 * <p>只验证降级通道（未配置 api-key 或大模型不可用时）：
 * 不启动 Spring 容器、不发起任何外部请求，保证离线可回归。
 */
class AiScoreServiceImplTest {

    private final AiScoreServiceImpl service = new AiScoreServiceImpl();

    /** 构造一条简答题答题明细 */
    private ExamAnswer answer(String userAnswer, String correctAnswer, String questionScore) {
        ExamAnswer answer = new ExamAnswer();
        answer.setId(1L);
        answer.setQuestionType(5);
        answer.setContent("请说明需求分析的作用。");
        answer.setUserAnswer(userAnswer);
        answer.setCorrectAnswer(correctAnswer);
        answer.setQuestionScore(new BigDecimal(questionScore));
        return answer;
    }

    @Test
    @DisplayName("未作答：直接 0 分，不产生命中要点，且不调用大模型")
    void emptyAnswerScoresZero() {
        AiScoreResult result = service.localScore(
                answer("   ", "明确需求范围；降低返工风险", "10"), new BigDecimal("10"), null);

        assertEquals(0, result.getScore().compareTo(BigDecimal.ZERO));
        assertTrue(result.getHits().isEmpty(), "未作答不应命中任何要点");
        assertEquals(2, result.getMissing().size(), "缺失要点应列出参考答案全部要点");
        assertEquals("local", result.getSource());
        assertTrue(result.getComment().contains("未作答"), "点评应说明未作答");
    }

    @Test
    @DisplayName("完整覆盖评分要点：接近满分且绝不超分")
    void fullCoverageScoresHigh() {
        String reference = "明确需求范围；降低返工风险；为设计提供依据";
        String userAnswer = "需求分析可以明确需求范围，降低返工风险，并为后续设计提供依据";

        AiScoreResult result = service.localScore(
                answer(userAnswer, reference, "10"), new BigDecimal("10"), null);

        assertTrue(result.getScore().compareTo(new BigDecimal("7.0")) >= 0,
                "要点全覆盖应得高分，实际：" + result.getScore());
        assertTrue(result.getScore().compareTo(new BigDecimal("10")) <= 0, "得分不得超过满分");
        assertTrue(result.getMissing().isEmpty(), "要点全覆盖不应有缺失要点");
        assertEquals("local", result.getSource());
    }

    @Test
    @DisplayName("只答到部分要点：给中间分，且明显低于满分")
    void partialCoverageScoresMiddle() {
        String reference = "明确需求范围；降低返工风险；为设计提供依据；便于估算工作量";
        String userAnswer = "需求分析可以明确需求范围";

        AiScoreResult result = service.localScore(
                answer(userAnswer, reference, "10"), new BigDecimal("10"), null);

        assertTrue(result.getScore().compareTo(BigDecimal.ZERO) > 0,
                "答到要点应给分，实际：" + result.getScore());
        assertTrue(result.getScore().compareTo(new BigDecimal("7.0")) < 0,
                "覆盖不全不应接近满分，实际：" + result.getScore());
        assertFalse(result.getMissing().isEmpty(), "应给出缺失要点供教师复核");
        assertNotNull(result.getReason());
    }

    @Test
    @DisplayName("满分非 0.5 整数倍：按 0.5 取整后仍不超过满分（否则保存会被判超分）")
    void scoreNeverExceedsMaxScore() {
        String reference = "明确需求范围；降低返工风险；为设计提供依据";
        String userAnswer = "需求分析可以明确需求范围，降低返工风险，并为后续设计提供依据";

        AiScoreResult result = service.localScore(
                answer(userAnswer, reference, "9.8"), new BigDecimal("9.8"), null);

        assertTrue(result.getScore().compareTo(new BigDecimal("9.8")) <= 0,
                "取整后仍不得超过满分 9.8，实际：" + result.getScore());
    }

    @Test
    @DisplayName("参考答案缺失：不抛异常，按相似度估算并给出人工复核提示")
    void missingReferenceStillReturnsScore() {
        AiScoreResult result = service.localScore(
                answer("需求分析就是梳理清楚要做什么", "", "10"), new BigDecimal("10"), null);

        assertNotNull(result.getScore());
        assertTrue(result.getScore().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(result.getReason().contains("未维护参考答案"), "应提示该题未维护参考答案");
    }
}
