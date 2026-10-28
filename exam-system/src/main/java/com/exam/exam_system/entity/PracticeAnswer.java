package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生练习答题明细，对应 practice_answer 表
 */
@Data
public class PracticeAnswer {

    private Long id;

    /** 练习记录ID */
    private Long practiceRecordId;

    /** 练习卷ID（冗余） */
    private Long practicePaperId;

    /** 试题ID */
    private Long questionId;

    /** 题目序号 */
    private Integer sortNo;

    /** 题型快照 */
    private Integer questionType;

    /** 题干（联表查询） */
    private String questionContent;

    /** 选项（联表查询） */
    private String options;

    /** 该题满分 */
    private Integer score;

    /** 学生得分 */
    private Integer getScore;

    /** 学生作答 */
    private String userAnswer;

    /** 正确答案快照 */
    private String correctAnswer;

    /** 是否答对：0错 1对（主观题批改前为 NULL） */
    private Integer isCorrect;

    /** 是否客观题：1客观题（提交即判分） 0主观题（需教师批改） */
    private Integer isObjective;

    /** 教师批改得分（主观题） */
    private Integer reviewScore;

    /** 批改状态：0待批改 1已批改（客观题提交即视为已批改） */
    private Integer reviewStatus;

    /** 教师评语 */
    private String reviewOpinion;

    /** 批改教师ID */
    private Long reviewUserId;

    /** 批改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;

    /** 解析（联表查询） */
    private String analysis;
}
