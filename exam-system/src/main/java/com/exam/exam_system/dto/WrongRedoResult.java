package com.exam.exam_system.dto;

import lombok.Data;

/** 错题本重做结果 */
@Data
public class WrongRedoResult {
    /** 是否答对 */
    private Boolean correct;
    /** 重做后的累计做对次数 */
    private Integer correctCount;
    /** 是否已移出错题本（达到策略阈值） */
    private Boolean removed;
    /** 标准答案（答错时回传展示） */
    private String correctAnswer;
    /** 本次提交的答案（答错时前端可即时刷新列表里的「我的答案」） */
    private String userAnswer;
}
