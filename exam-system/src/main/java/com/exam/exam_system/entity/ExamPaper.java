package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试卷实体，对应 exam_paper 表
 *
 * <pre>
 * 字段对照（数据库 → 实体属性，MyBatis 驼峰自动映射）：
 * id              → id              试卷ID
 * paper_name      → paperName       试卷名称
 * course_id       → courseId        所属课程
 * paper_type      → paperType       组卷方式：1手动 2随机
 * total_score     → totalScore      总分
 * duration        → duration        考试时长（分钟）
 * pass_score      → passScore       及格分（自动换算：满分 × 60%，不由教师填写）
 * creator_id      → creatorId       创建人
 * status          → status          状态：0草稿 1已发布 2已结束
 * start_time      → startTime       考试开始
 * end_time        → endTime         考试结束
 * create_time     → createTime      创建时间
 * update_time     → updateTime      更新时间
 * </pre>
 */
@Data
public class ExamPaper {

    private Long id;
    private String paperName;
    private Long courseId;

    /** 学科名称（非表字段，列表页由 SQL 联表填充） */
    private String courseName;

    /** 创建人姓名（非表字段，列表页由 SQL 联表填充） */
    private String creatorName;

    /** 组卷方式：1手动 2随机 */
    private Integer paperType;

    private BigDecimal totalScore;

    /** 考试时长（分钟） */
    private Integer duration;

    private BigDecimal passScore;
    private Long creatorId;

    /**
     * 状态：0草稿 / 1已发布 / 2已归档
     * 注："已结束"是 exam 表的运行时状态，不属于试卷本身
     */
    private Integer status;

    /** 逻辑删除：0正常 / 1已删除（保留历史考试成绩数据） */
    private Integer isDeleted;

    /**
     * 共享给哪些教师（非表字段，前端入参）
     * 提交时由 Controller 接收 List 形式，Service 写入中间表 exam_paper_visible_user
     */
    private java.util.List<Long> visibleUserIds;

    /** 可见教师姓名（非表字段，列表页由服务层按中间表 JOIN 填充，如 "陈平安、李四"） */
    private String visibleNames;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 题目数量（非表字段，列表页由 SQL 子查询计算填充）
     */
    private Integer questionCount;
}
