package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 答题明细实体，对应 exam_answer 表（一题一行）
 */
@Data
public class ExamAnswer {

    private Long id;

    /** 关联 exam_record.id */
    private Long recordId;

    private Long questionId;

    /** 学生作答（单选"A" 多选"A,C" 判断"T" 填空/简答文本） */
    private String userAnswer;

    /** 1=对 0=错（主观题为 NULL，待教师判） */
    private Integer isCorrect;

    /** 该题得分（客观题自动得分 / 主观题教师阅卷得分） */
    private BigDecimal getScore;

    /** 教师评语（旧字段，保留兼容） */
    private String teacherComment;

    private LocalDateTime createTime;

    // ========== 新增阅卷字段 ==========

    /** 0主观题 1客观题 */
    private Integer isObjective;

    /** 阅卷状态：0待阅卷 1已阅卷 */
    private Integer reviewStatus;

    /** 教师给的主观题得分 */
    private BigDecimal reviewScore;

    /** 阅卷评语 */
    private String reviewOpinion;

    /** 阅卷教师ID */
    private Long reviewUserId;

    /** 阅卷时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;

    // ========== 非表字段，联表回显 ==========

    /** 题干（联 question 表） */
    private String content;

    /** 题型（联 question 表） */
    private Integer questionType;

    /** 试题满分（联 paper_question 表） */
    private BigDecimal questionScore;

    /** 标准答案（联 question 表） */
    private String correctAnswer;

    /** 学生姓名（联 sys_user 表） */
    private String realName;

    /** 学生学号（联 sys_user 表） */
    private String username;

    /** 班级名称（联 sys_user → sys_class） */
    private String className;

    /** 考试ID（联 exam 表） */
    private Long examId;

    /** 考试名称（联 exam 表） */
    private String examName;

    /** 试卷名称（联 exam_paper 表） */
    private String paperName;
}
