package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目实体，对应 question 表
 *
 * <p>字段对照（数据库 → 实体属性，MyBatis 驼峰自动映射）：
 * <pre>
 * id              → id              题目ID
 * course_id       → courseId        所属课程
 * question_type   → questionType    题型：1=单选 2=多选 3=判断 4=填空 5=简答
 * content         → content         题干
 * options         → options         选项JSON数组（仅单选/多选），如 ["选项A","选项B","选项C","选项D"]
 * answer          → answer          答案
 * analysis        → analysis        解析
 * difficulty      → difficulty      难度：1~5
 * knowledge_id    → knowledgeId     主知识点（关联表 question_knowledge 中第一条）
 * score           → score           默认分值
 * creator_id      → creatorId       出题人
 * create_time     → createTime      创建时间
 * update_time     → updateTime      更新时间
 * audit_status    → auditStatus     审核状态：0待审核 1通过 2驳回
 * audit_opinion   → auditOpinion    审核驳回意见
 * audit_time      → auditTime       审核时间
 * audit_user_id   → auditUserId     审核人（管理员）
 * auditor_name    → auditorName     审核人姓名（联表查出，非表字段）
 * submitter_name  → submitterName   提交人姓名（联表查出，非表字段）
 * </pre>
 */
@Data
public class Question {

    private Long id;
    private Long courseId;

    /** 学科名称（非 question 表字段，联表 sys_course 查出，列表展示用） */
    private String courseName;

    /** 主知识点名称（非 question 表字段，联表 knowledge_point 查出，列表/详情展示用） */
    private String knowledgeName;

    /**
     * 完整知识点 ID 集合（一道题可关联 1~3 个知识点）。
     * 新增/编辑题目时由前端 JSON 数组传入；列表/详情查询时由 knowledgeIdsCsv 解析回填。
     * 非 question 表字段，对应 question_knowledge 关联表。
     */
    private List<Long> knowledgeIds;

    /**
     * 知识点 ID 逗号分隔串（内部中间字段，SQL 子查询 GROUP_CONCAT 回填后由 Service 拆分为 knowledgeIds）。
     * 非 question 表字段，不参与 JSON 序列化。
     */
    @JsonIgnore
    private String knowledgeIdsCsv;

    /**
     * 全部知识点名称（顿号分隔，如"基础语法、集合框架"，列表/详情展示用）。
     * 与 knowledgeName（主知识点名）并存；非 question 表字段。
     */
    private String knowledgeNames;

    /** 题型：1=单选 2=多选 3=判断 4=填空 5=简答 */
    private Integer questionType;

    /** 题干 */
    private String content;

    /**
     * 选项 JSON 数组字符串，仅单选/多选题使用。
     * 形如 ["选项A内容","选项B内容","选项C内容","选项D内容"]。
     * 其他题型为 null。
     */
    private String options;

    /** 参考答案：单选存 A，多选存 A,B,C，判断存 T/F，填空/简答存答案文本 */
    private String answer;

    /** 答案解析 */
    private String analysis;

    /** 难度：1~5 */
    private Integer difficulty;

    /** 关联知识点 ID */
    private Long knowledgeId;

    /** 默认分值 */
    private BigDecimal score;

    /** 出题人（sys_user.id） */
    private Long creatorId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 审核状态：0待审核 1通过 2驳回 */
    private Integer auditStatus;

    /** 审核驳回意见 */
    private String auditOpinion;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditTime;

    /** 审核人（管理员用户ID） */
    private Long auditUserId;

    /** 审核人姓名（联表查出，非 question 表字段） */
    private String auditorName;

    /** 提交人姓名（联表查出，非 question 表字段） */
    private String submitterName;

    /**
     * 题库重复题 ID（逗号分隔，非 question 表字段，仅待审核列表由服务端查重后回填）。
     * 非空表示本学科题库已存在同题干且审核通过的正式题目，前端据此标记"与题库重复"。
     */
    private String duplicateBankIds;
}
