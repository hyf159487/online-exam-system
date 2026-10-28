package com.exam.exam_system.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组卷模板列表展示 VO（联表查询结果，含课程名、创建人名）
 */
@Data
public class PaperTemplateVO {

    private Long id;
    private String templateName;
    private Long courseId;
    private String courseName;
    private Integer totalScore;
    private Integer totalQuestionCount;

    /** 组卷方式：1手动 2随机 3AI智能（列表展示用） */
    private Integer paperType;

    private Long creatorId;
    private String creatorName;
    private String remark;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
