package com.exam.exam_system.dto;

import lombok.Data;

/** 自主练习：单题判分结果 */
@Data
public class PracticeSubmitResult {
    /** 是否答对 */
    private Boolean correct;
    /** 标准答案（答对也回传，便于前端展示） */
    private String correctAnswer;
    /** 解析 */
    private String analysis;
    /** 该题是否被归集进错题本（答错且未重复时才会 true） */
    private Boolean collected;
    /**
     * 错题强化练习（fromWrong=true）时回传：本题当前「连续答对次数」。
     * 非错题来源或本题不在错题本中时为 null。
     */
    private Integer correctCount;
    /** 错题强化练习（fromWrong=true）时回传：本次答对是否已达到移除策略阈值并自动移出错题本 */
    private Boolean removed;
}
