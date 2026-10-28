package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统通知实体，对应 sys_notice 表
 *
 * <p>教师/管理员发布的站内消息，每个接收学生一条 sys_user_notice 记录。
 *
 * <p>字段对照：
 * <pre>
 * id                → id                主键
 * title             → title             通知标题
 * content           → content           通知正文
 * receiveType       → receive_type      接收对象：1全部学生 2指定班级 3指定考试考生
 * targetClassIds    → target_class_ids  班级ID集合（逗号分隔，receiveType=2）
 * targetExamId      → target_exam_id    考试ID（receiveType=3）
 * sendUserId        → send_user_id      发送人ID
 * sendUserName      → send_user_name    发送人姓名（冗余）
 * sendTime          → send_time         发送时间
 * isDeleted         → is_deleted        逻辑删除
 * </pre>
 */
@Data
public class SysNotice {

    private Long id;

    /** 通知标题 */
    private String title;

    /** 通知正文 */
    private String content;

    /** 接收对象：1全部学生 2指定班级 3指定考试考生 */
    private Integer receiveType;

    /** 班级ID集合（逗号分隔，receiveType=2 时使用） */
    private String targetClassIds;

    /** 考试ID（receiveType=3 时使用） */
    private Long targetExamId;

    /** 发送人ID（教师/管理员） */
    private Long sendUserId;

    /** 发送人姓名（冗余） */
    private String sendUserName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sendTime;

    /** 逻辑删除：0未删 1已删 */
    private Integer isDeleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // ===== 非表字段：列表页显示用 =====

    /** 接收范围描述（列表页显示） */
    private String receiveRangeText;

    /** 接收学生数（列表页显示） */
    private Integer receiveCount;

    /** 已确认收到人数（列表页显示，子查询统计） */
    private Integer confirmedCount;
}
