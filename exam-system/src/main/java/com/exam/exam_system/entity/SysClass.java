package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 班级实体，对应 sys_class 表
 */
@Data
public class SysClass {

    private Long id;

    /** 班级名称，如「23005801」 */
    private String className;

    /** 年级，如「2023级」 */
    private String grade;

    /** 院系 */
    private String department;

    /** 专业 */
    private String major;

    /** 班主任ID（sys_user.id，role=1 教师） */
    private Long teacherId;

    /** 任课教师姓名（联表 sys_user 查出，非表字段，列表展示用） */
    private String teacherName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 班级学生数（联 sys_user 统计，非表字段，列表展示用） */
    private Integer studentCount;
}
