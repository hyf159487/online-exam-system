package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试实体，对应 exam 表（考试发布表）
 *
 * <p>一场考试 = 某张试卷 + 时间窗口。
 * 运行状态（runtimeStatus）不存库，由当前时间和时间窗口实时推导：
 * <ul>
 *     <li>0 = 未开始（now < start_time）</li>
 *     <li>1 = 进行中（start_time <= now <= end_time）</li>
 *     <li>2 = 已结束（now > end_time）</li>
 *     <li>3 = 已撤回（status=0 时覆盖一切）</li>
 * </ul>
 */
@Data
public class Exam {

    private Long id;

    /** 考试名称 */
    private String examName;

    /** 关联试卷 ID */
    private Long paperId;

    /** 学科 ID（冗余自试卷，方便筛选） */
    private Long courseId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 考试时长（分钟） */
    private Integer duration;

    /** 总分（冗余自试卷，发布时快照） */
    private BigDecimal totalScore;

    /** 及格分（冗余自试卷，发布时快照） */
    private BigDecimal passScore;

    /** 1=已发布 0=已撤回 */
    private Integer status;

    /** 发布人 */
    private Long creatorId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // ========== 以下为非表字段，联表/推导回显用 ==========

    /** 试卷名称（联 exam_paper 查出） */
    private String paperName;

    /** 试卷题数（联 exam_paper 查出） */
    private Integer questionCount;

    /** 运行状态：0未开始 1进行中 2已结束 3已撤回（Service 层推导） */
    private Integer runtimeStatus;

    /** 学科名称（联 sys_course 查出，非表字段） */
    private String courseName;

    // ========== 学生端扩展字段（非表字段） ==========

    /** 当前学生是否已参加过（有考试记录时为记录ID，否则 null） */
    private Long myRecordId;

    /** 当前学生的得分（已参加时回显） */
    private BigDecimal myScore;
}
