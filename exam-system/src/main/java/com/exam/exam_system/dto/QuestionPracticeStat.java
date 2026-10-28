package com.exam.exam_system.dto;

import lombok.Data;

/**
 * 某道题的练习统计（学生答题后展示「我已做过几次，正确率多少」）
 */
@Data
public class QuestionPracticeStat {

    /** 该学生在这道题上的总作答次数 */
    private Integer doneCount;

    /** 该学生在这道题上的答对次数 */
    private Integer correctCount;
}
