package com.exam.exam_system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 主观题阅卷-待阅/已阅列表 VO
 */
@Data
public class ReviewWaitVO {

    private Long answerId;
    private Long recordId;
    private Long questionId;
    private String content;
    private Integer questionType;
    private BigDecimal questionScore;
    private String userAnswer;
    private BigDecimal reviewScore;
    private String reviewOpinion;
    private Integer reviewStatus;

    // ========== 联表回显 ==========
    private Long examId;
    private String examName;
    private Long studentId;
    private String realName;
    private String username;
    private String className;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submitTime;

    /** 剩余待阅数量（非列表字段，统计用） */
    private Integer pendingCount;
}
