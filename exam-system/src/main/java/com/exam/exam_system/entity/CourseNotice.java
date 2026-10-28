package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程公告实体，对应 course_notice 表
 *
 * <p>由该课程的任课教师（或管理员）在课程内发布，仅该课程学生可见。
 * 与系统级通知（sys_notice）区分：课程公告归属某门课程、按课程维度组织，
 * 不参与「通知公告」的接收范围（全部学生 / 指定班级 / 指定考试）计算。
 */
@Data
public class CourseNotice {

    private Long id;

    /** 所属课程ID（sys_course.id） */
    private Long courseId;

    /** 课程名称（联查展示用，非表字段） */
    private String courseName;

    /** 公告标题 */
    private String title;

    /** 公告正文 */
    private String content;

    /** 发布教师ID（sys_user.id） */
    private Long teacherId;

    /** 发布教师姓名（冗余存储，列表直接展示） */
    private String teacherName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0=正常 1=已删除 */
    private Integer isDeleted;
}
