package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 练习卷实体，对应 practice_paper 表
 * 状态：0草稿 1已发布（学生可见） 2停用
 */
@Data
public class PracticePaper {

    private Long id;

    /** 练习卷名称 */
    private String paperName;

    /** 所属课程ID */
    private Long courseId;

    /** 课程名称（联表冗余显示） */
    private String courseName;

    /** 描述 */
    private String description;

    /** 总分 */
    private Integer totalScore;

    /** 题目数量 */
    private Integer questionCount;

    /** 状态：0草稿 1已发布 2停用 */
    private Integer status;

    /** 创建教师ID */
    private Long createTeacherId;

    /** 创建教师姓名（冗余） */
    private String createTeacherName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 逻辑删除：0未删 1已删 */
    private Integer isDeleted;

    // ===== 文件型作业：作业内容为一份附件文件（学生下载/在线查看，线下作答）=====

    /** 附件原始文件名 */
    private String attachmentName;

    /** 附件访问路径（形如 /uploads/homework/yyyyMM/xxx.docx） */
    private String attachmentPath;

    /** 附件字节数 */
    private Long attachmentSize;

    /** 附件类型归类：pdf/word/ppt/excel/text/compress/image */
    private String attachmentType;

    // ===== 非表字段：保存/详情时使用 =====

    /** 题目集合（保存时使用） */
    private List<PracticePaperQuestion> questions;

    /** 是否有练习记录（编辑时提示） */
    private Integer hasPracticeRecords;

    // ===== 非表字段：统计展示用 =====

    /** 总练习次数（practice_record 总数） */
    private Integer practiceCount;

    /** 练习人数（practice_record 去重 student_id） */
    private Integer practiceUserCount;

    /** 当前登录学生的个人练习次数（仅学生端查询时有值） */
    private Integer myPracticeCount;

    // ===== 非表字段：文件型作业「我的提交」（仅学生端查询时有值）=====

    /** 我对该作业最新一条提交记录ID（practice_record.id，无提交为 null） */
    private Long mySubmitId;

    /** 我的提交状态：0未提交 1待批改 2已批改 */
    private Integer mySubmitStatus;

    /** 我提交的附件原始文件名 */
    private String mySubmitAttachmentName;

    /** 我提交的附件访问路径 */
    private String mySubmitAttachmentPath;

    /** 我提交的附件字节数 */
    private Long mySubmitAttachmentSize;

    /** 我提交的备注 */
    private String mySubmitRemark;

    /** 我提交的时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime mySubmitTime;

    /** 教师给我的分数（文件型作业） */
    private Integer mySubmitScore;

    /** 教师给我的评语（文件型作业） */
    private String mySubmitOpinion;
}
