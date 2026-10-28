package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试题收藏实体，对应 question_favorite 表
 *
 * <p>学生在自主练习（练习卷 / 自由刷题）过程中手动标记的重点题目，集中到「我的收藏」页面复习。
 * 与错题本（wrong_question，系统自动归集答错的题）是两套独立的题目集合：
 * 错题 = 做错的题，收藏 = 自己标记的重点题。
 *
 * <p>表中只存 user_id / question_id / create_time；题干、选项、答案等通过联表 question 回显，
 * 下面的 content / options / answer / analysis / difficulty / questionType / courseName 均为非表字段。
 */
@Data
public class QuestionFavorite {

    /** 主键 */
    private Long id;

    /** 学生用户 ID（sys_user.id） */
    private Long userId;

    /** 题目 ID（question.id） */
    private Long questionId;

    /** 收藏时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // ========== 以下为非表字段，联 question / sys_course 回显用 ==========

    /** 题干（联 question 表） */
    private String content;

    /** 选项 JSON 数组（联 question 表，仅单选/多选有值） */
    private String options;

    /** 标准答案（联 question 表） */
    private String answer;

    /** 题目解析（联 question 表） */
    private String analysis;

    /** 题型：1=单选 2=多选 3=判断 4=填空 5=简答 6=名词解释 */
    private Integer questionType;

    /** 难度：1~5（1-2 简单 / 3 中等 / 4-5 困难） */
    private Integer difficulty;

    /** 题目默认分值（联 question 表） */
    private BigDecimal score;

    /** 学科 ID（联 question 表的 course_id） */
    private Long courseId;

    /** 学科名称（联 sys_course 表） */
    private String courseName;
}
