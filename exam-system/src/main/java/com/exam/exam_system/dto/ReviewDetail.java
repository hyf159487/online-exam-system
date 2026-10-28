package com.exam.exam_system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 阅卷详情 DTO：考试记录信息 + 学生信息 + 逐题阅卷信息
 */
@Data
public class ReviewDetail {

    /** 考试记录ID */
    private Long recordId;

    /** 试卷ID */
    private Long paperId;

    /** 试卷名称 */
    private String paperName;

    /** 学科名称 */
    private String courseName;

    /** 学号 */
    private String username;

    /** 学生姓名 */
    private String realName;

    /** 开考时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 交卷时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submitTime;

    /** 记录状态：1=待阅卷 2=已判分 */
    private Integer status;

    /** 客观题自动得分（只读展示） */
    private BigDecimal objectiveScore;

    /** 当前总分（客观+主观，判分前=客观分） */
    private BigDecimal totalScore;

    /** 试卷满分 */
    private BigDecimal paperTotalScore;

    /** 逐题信息 */
    private List<ReviewItem> questions;
}
