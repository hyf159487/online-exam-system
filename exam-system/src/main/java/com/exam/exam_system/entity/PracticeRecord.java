package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生练习记录，对应 practice_record 表
 */
@Data
public class PracticeRecord {

    private Long id;

    /** 练习卷ID */
    private Long practicePaperId;

    /** 练习卷名称（冗余） */
    private String paperName;

    /** 学生ID */
    private Long studentId;

    /** 学生姓名（冗余） */
    private String studentName;

    /** 班级名称（联表显示） */
    private String className;

    /** 本次练习得分 */
    private Integer score;

    /** 练习卷总分（快照） */
    private Integer totalScore;

    /** 答对题数 */
    private Integer correctCount;

    /** 答错题数 */
    private Integer wrongCount;

    /** 完成状态：0未完成 1已完成 */
    private Integer finishStatus;

    /** 批改状态：0无需批改 1待批改 2已批改 */
    private Integer reviewStatus;

    /** 主观题题数 */
    private Integer subjectiveCount;

    /** 待批改主观题数（列表联表统计，不入库） */
    private Integer pendingReviewCount;

    // ===== 文件型作业：学生提交附件 + 教师批改（附件提交记录 attachment_path 非空）=====

    /** 学生提交的作业附件原始文件名 */
    private String attachmentName;

    /** 学生提交的作业附件访问路径 /uploads/homework/yyyyMM/uuid.ext */
    private String attachmentPath;

    /** 学生提交附件字节数 */
    private Long attachmentSize;

    /** 学生提交附件类型归类：pdf/word/ppt/excel/text/compress/image */
    private String attachmentType;

    /** 学生提交备注（选填） */
    private String submitRemark;

    /** 教师评语（文件型作业没有逐题明细，评语直接落在记录上） */
    private String reviewOpinion;

    /** 批改教师ID */
    private Long reviewUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
