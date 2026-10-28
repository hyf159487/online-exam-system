package com.exam.exam_system.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 交卷结果 DTO：判分完成后立刻返回给学生
 */
@Data
public class SubmitResult {

    /** 得分（客观题自动判分；含简答时为主观题待评分的暂定分） */
    private BigDecimal totalScore;

    /** 试卷满分 */
    private BigDecimal paperTotalScore;

    /** 及格分 */
    private BigDecimal passScore;

    /** 1=及格 0=不及格 */
    private Integer isPass;

    /** 客观题自动判分；含简答时为 1（待教师评分），否则为 2（已判分） */
    private Integer status;

    /** 主观题数量（>0 表示需要等老师阅卷） */
    private Integer subjectiveCount;
}
