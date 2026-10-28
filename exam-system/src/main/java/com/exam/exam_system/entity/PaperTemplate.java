package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组卷模板实体，对应 paper_template 表
 *
 * <p>字段对照（数据库 → 实体属性，MyBatis 驼峰自动映射）：
 * <pre>
 * id                    → id                  主键
 * template_name         → templateName        模板名称
 * course_id             → courseId            所属课程
 * total_score           → totalScore          试卷总分
 * total_question_count  → totalQuestionCount  总题目数
 * template_json         → templateJson        组卷明细JSON
 * paper_type            → paperType           组卷方式：1手动 2随机 3AI智能
 * ai_requirement        → aiRequirement       AI 组卷补充说明（paperType=3 时使用）
 * creator_id            → creatorId           创建人
 * create_time           → createTime          创建时间
 * remark                → remark              备注
 * </pre>
 */
@Data
public class PaperTemplate {

    private Long id;
    private String templateName;
    private Long courseId;
    private Integer totalScore;
    private Integer totalQuestionCount;

    /** 组卷明细 JSON 字符串，格式同 RandomComposeRequest.rules */
    private String templateJson;

    /**
     * 组卷方式，取值与 exam_paper.paper_type 一致：
     * 1 手动组卷（模板只保存基本信息，题目到组卷页手选）
     * 2 随机组卷（按 templateJson 明细自动抽题）
     * 3 AI 智能组卷（templateJson 作为题型/分值参考 + aiRequirement 自然语言要求）
     * 空值按 2 随机组卷处理（历史数据）
     */
    private Integer paperType;

    /** AI 智能组卷的补充说明，仅 paperType = 3 时有意义 */
    private String aiRequirement;

    private Long creatorId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String remark;
}
