package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 试题审核流水实体，对应 question_audit_log 表
 *
 * <p>审核留痕记录：每次审核通过 / 驳回 / 教师重新提交都会新增一条流水，
 * 只插入不修改不删除，保证多次提交多次审核全程可追溯。
 *
 * <p>字段对照（数据库 → 实体属性，MyBatis 驼峰自动映射）：
 * <pre>
 * id               → id               流水ID
 * question_id      → questionId       题目ID
 * action           → action           动作：1=审核通过 2=审核驳回 3=重新提交
 * opinion          → opinion          驳回原因（仅驳回动作填写）
 * content_snapshot → contentSnapshot  操作时题干快照
 * submitter_id     → submitterId      提交教师ID
 * auditor_id       → auditorId        审核操作人ID（重新提交流水为空）
 * audit_time       → auditTime        操作时间
 * submitter_name   → submitterName    提交教师姓名（联表查出，非表字段）
 * auditor_name     → auditorName      审核操作人姓名（联表查出，非表字段）
 * question_type    → questionType     题型（联 question 表查出，非表字段）
 * difficulty       → difficulty       难度（联 question 表查出，非表字段）
 * course_name      → courseName       所属课程（联表查出，非表字段）
 * create_time      → createTime       题目首次提交时间（联表查出，非表字段）
 * </pre>
 */
@Data
public class QuestionAuditLog {

    /** 动作：审核通过 */
    public static final int ACTION_PASS = 1;
    /** 动作：审核驳回 */
    public static final int ACTION_REJECT = 2;
    /** 动作：教师重新提交 */
    public static final int ACTION_RESUBMIT = 3;

    private Long id;

    private Long questionId;

    /** 动作：1=审核通过 2=审核驳回 3=重新提交 */
    private Integer action;

    /** 驳回原因（仅驳回动作填写） */
    private String opinion;

    /** 操作时题干快照 */
    private String contentSnapshot;

    private Long submitterId;

    private Long auditorId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime auditTime;

    // ===== 以下为联表查询补充字段（非表字段） =====

    /** 提交教师姓名 */
    private String submitterName;

    /** 审核操作人姓名 */
    private String auditorName;

    /** 题型（联 question 表） */
    private Integer questionType;

    /** 难度（联 question 表） */
    private Integer difficulty;

    /** 所属课程名（联 sys_course 表） */
    private String courseName;

    /** 题目首次提交时间（联 question 表） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
