package com.exam.exam_system.entity;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 错题本实体，对应 wrong_question 表
 *
 * <p>学生在考试中答错的客观题（单选/多选/判断/填空），交卷时由后端自动归集到这里。
 * 同一道题对同一个学生只保留一条（数据库 uk_user_question 唯一约束 + 后端 find-then-update 双保险，
 * 再次答错会刷新答案并 wrong_count + 1），学生端可在“错题本”里查看、重做，重做正确后从错题本移除。
 */
@Data
public class WrongQuestion {

    /** 主键 */
    private Long id;

    /** 学生用户 ID */
    private Long userId;

    /** 题目 ID（关联 question 表） */
    private Long questionId;

    /** 所属考试 ID（归集来源，可空） */
    private Long examId;

    /** 所属试卷 ID */
    private Long paperId;

    /** 学科 ID（冗余，方便按学科筛选） */
    private Long courseId;

    /** 题型：1=单选 2=多选 3=判断 4=填空 5=简答 */
    private Integer questionType;

    /** 学生当时提交的（错误）答案 */
    private String userAnswer;

    /** 标准答案 */
    private String correctAnswer;

    /** 该题分值 */
    private BigDecimal score;

    /** 所属考试记录 ID（exam_record.id，可空） */
    private Long recordId;

    /** 错误次数（每答错一次 +1，复用 DB 默认 1） */
    private Integer wrongCount;

    /** 连续答对次数：重做/练习做对 +1，一旦答错清零；达到 wrong_remove_threshold 阈值后自动移出错题本 */
    private Integer correctCount;

    /** 最近一次答错时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastWrongTime;

    /** 最近一次作答时间（考试、自主练习、错题重做都会刷新，与「最近错误」区分开） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastAnswerTime;

    // ========== 以下为非表字段，联 question / sys_course 回显用 ==========

    /** 题干（联 question 表） */
    private String content;

    /** 选项 JSON（联 question 表，仅单选/多选有值） */
    private String options;

    /** 学科名称（联 sys_course 表） */
    private String courseName;

    /** 题目标准解析（联 question 表，AI 讲解时参考用） */
    private String analysis;
}
