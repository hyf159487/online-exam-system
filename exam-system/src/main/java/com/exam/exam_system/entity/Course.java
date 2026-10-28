package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程（学科）实体，对应 sys_course 表
 */
@Data
public class Course {

    private Long id;

    /** 学科名称，如「Java 程序设计基础」 */
    private String courseName;

    /** 学科编码，如 CS101 */
    private String courseCode;

    /** 课程类型，如专业课、公共课、选修课 */
    private String courseType;

    /** 学科描述（卡片上的简短简介，varchar(200)） */
    private String description;

    /** 课程详细介绍（学生端「课程详情 → 课程介绍」Tab 的正文，与卡片简短描述区分，可空） */
    private String intro;

    /** 学习目标（课程详情页展示，可空） */
    private String learningObjectives;

    /** 适用人群（课程详情页展示，可空） */
    private String targetAudience;

    /** 课程大纲（每行一个章节，课程详情页按行渲染，可空） */
    private String syllabus;

    /** 课程封面图访问路径（形如 /uploads/course-cover/202609/xxx.jpg），由课程管理上传，可空 */
    private String cover;

    /** 开课时间（学生端展示课程何时开始，可空） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 报名截止时间（为空=长期开放报名，非空且已过则学生端显示【报名截止】禁用） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime enrollDeadline;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 绑定的任课教师ID列表（新增/编辑时前端提交，非表字段） */
    private List<Long> teacherIds;

    /** 任课教师姓名，逗号拼接（列表联查聚合，非表字段，展示用） */
    private String teacherNames;

    /** 已加入学生数（列表联查统计，非表字段，学生端课程卡片「参加人数」用） */
    private Integer studentCount;
}
