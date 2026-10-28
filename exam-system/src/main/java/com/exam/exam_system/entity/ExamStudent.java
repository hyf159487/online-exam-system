package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 考试监控-学生实时状态实体，对应 exam_student 表
 *
 * <p>状态：0=未开始 1=考试中 2=已交卷 3=强制交卷
 */
@Data
public class ExamStudent {

    private Long id;
    private Long examId;
    private Long studentId;

    /** 0未开始 1考试中 2已交卷 3强制交卷 */
    private Integer status;

    private Integer answeredCount;
    private Integer totalCount;
    private Integer remainingTime;
    private Integer screenSwitchCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime enterTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime leaveTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // ========== 非表字段，联表回显 ==========

    /** 学生学号（联 sys_user） */
    private String username;

    /** 学生姓名（联 sys_user） */
    private String realName;

    /** 班级名称（联 sys_user -> sys_class） */
    private String className;
}
