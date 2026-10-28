package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生申请加入课程记录，对应 course_apply 表
 *
 * <p>applyType：0加入申请 1退出申请；status：0待审核 1已通过 2已驳回。
 *
 * <p>以下为联查展示字段（非表字段）：
 * studentName（学生姓名）、className（学生班级）、courseName（课程名）、
 * courseDescription（课程简介）、teacherNames（任课教师，逗号/顿号拼接）。
 */
@Data
public class CourseApply {

    private Long id;

    private Long studentId;

    private Long courseId;

    /** 0加入申请 1退出申请 */
    private Integer applyType;

    /** 0待审核 1已通过 2已驳回 */
    private Integer status;

    private String rejectReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime applyTime;

    private Long auditTeacherId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditTime;

    private Integer isDeleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // ===== 联查展示字段 =====

    private String studentName;

    private String studentUsername;

    private String className;

    private String courseName;

    private String courseCode;

    private String courseDescription;

    private String teacherNames;
}
