package com.exam.exam_system.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 主观题 AI 预评分结果
 *
 * <p>仅作为「阅卷建议」返回给教师，不会直接落库：
 * 教师看到建议得分与评分依据后，可一键采纳或手工调整，再走 save-score 保存。
 *
 * <p>source 取值：
 * <ul>
 *     <li>llm  ：已配置 ai.api-key，由大模型评分</li>
 *     <li>local：未配置 key 或大模型调用失败，降级为本地规则引擎</li>
 * </ul>
 */
@Data
public class AiScoreResult {

    /** 答题明细ID（exam_answer.id） */
    private Long answerId;

    /** AI 建议得分（已按 0.5 分档取整，且不超过满分） */
    private BigDecimal score;

    /** 该题满分（未取到时按 10 分估算，并在 warning 中说明） */
    private BigDecimal maxScore;

    /** 建议评语：面向学生，可直接写入 review_opinion */
    private String comment;

    /** 评分依据：面向教师，说明扣分/给分原因 */
    private String reason;

    /** 命中的评分要点 */
    private List<String> hits = new ArrayList<>();

    /** 缺失的评分要点 */
    private List<String> missing = new ArrayList<>();

    /** 置信度 0~1，越低越需要教师复核 */
    private BigDecimal confidence;

    /** 结果来源：llm / local */
    private String source;

    /** 大模型名称（source=llm 时有值） */
    private String model;

    /** 提示信息：降级原因、满分缺失等，供前端提示教师 */
    private String warning;
}
