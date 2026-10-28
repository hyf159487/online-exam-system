package com.exam.exam_system.dto;

import lombok.Data;

/**
 * 文件型作业批改：教师在「批改作业」里给学生的提交判分 + 写评语
 *
 * <p>文件型作业没有逐题明细（practice_answer 为空），得分与评语直接落在
 * practice_record 上（score / review_opinion），review_status 置为 2 已批改。
 */
@Data
public class PracticeAttachmentReview {

    /** 教师给的分数（0 ~ 满分；作业未设总分时上限按 100 计） */
    private Integer score;

    /** 评语（选填，<=255 字） */
    private String opinion;
}
