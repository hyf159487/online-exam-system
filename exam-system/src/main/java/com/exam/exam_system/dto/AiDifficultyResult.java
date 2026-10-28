package com.exam.exam_system.dto;

import lombok.Data;

/**
 * AI 题目难度评估结果
 *
 * <p>source 区分评估来源：
 * <ul>
 *     <li>llm   —— 真实大模型评估（已配置 api-key）</li>
 *     <li>local —— 本地规则启发式评估（未配置 api-key 或调用失败降级）</li>
 * </ul>
 */
@Data
public class AiDifficultyResult {

    /** 评估的难度等级：1=简单 2=中等 3=困难 */
    private Integer difficulty;

    /** 难度中文描述（供前端提示）：简单/中等/困难 */
    private String difficultyText;

    /** 来源：llm=大模型 / local=本地规则 */
    private String source;

    /** 使用的模型名（大模型时才有值） */
    private String model;

    /** 评估理由（大模型时返回简要说明，本地规则时返回命中的规则摘要） */
    private String reason;

    // —— 便捷工厂方法 ——

    public static AiDifficultyResult ofLocal(int difficulty, String reason) {
        AiDifficultyResult r = new AiDifficultyResult();
        r.setDifficulty(difficulty);
        r.setDifficultyText(difficultyTextOf(difficulty));
        r.setSource("local");
        r.setReason(reason);
        return r;
    }

    public static AiDifficultyResult ofLlm(int difficulty, String model, String reason) {
        AiDifficultyResult r = new AiDifficultyResult();
        r.setDifficulty(difficulty);
        r.setDifficultyText(difficultyTextOf(difficulty));
        r.setSource("llm");
        r.setModel(model);
        r.setReason(reason);
        return r;
    }

    public static String difficultyTextOf(int d) {
        return switch (d) {
            case 1 -> "简单";
            case 2 -> "中等";
            case 3 -> "困难";
            default -> "中等";
        };
    }
}
