package com.exam.exam_system.dto;

import lombok.Data;

/** 自主练习：单题作答提交 */
@Data
public class PracticeSubmitRequest {
    /** 题目 ID */
    private Long questionId;
    /** 学生提交的答案（单选=A 多选=A,B 判断=T/F 填空=文本） */
    private String userAnswer;
    /**
     * 是否来自「我的错题」的强化练习。
     * 为 true 时，判分后同步错题本：答对累加连续答对次数（达阈值自动移出），答错清零。
     */
    private Boolean fromWrong;
}
