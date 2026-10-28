package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生作答记录，对应 student_practice_log 表
 *
 * <p>每次「单题作答」都落一条（无论对错），用于统计某学科下学生已练习题数（去重）
 * 以及后续的学习诊断。来源覆盖：1=按学科自由刷题；2=练习卷整套作答。
 */
@Data
public class StudentPracticeLog {

    private Long id;

    /** 学生用户 ID */
    private Long userId;

    /** 题目 ID（关联 question 表） */
    private Long questionId;

    /** 所属学科 ID（冗余，方便按学科统计） */
    private Long courseId;

    /** 题型：1=单选 2=多选 3=判断 4=填空 5=简答 */
    private Integer questionType;

    /** 来源：1=自由刷题单题；2=练习卷整套 */
    private Integer source;

    /** 学生作答 */
    private String userAnswer;

    /** 是否答对：1对 0错 （主观题不判分时为 NULL） */
    private Integer isCorrect;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
