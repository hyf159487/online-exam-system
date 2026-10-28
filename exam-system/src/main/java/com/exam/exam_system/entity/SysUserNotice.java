package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户通知接收记录实体，对应 sys_user_notice 表
 *
 * <p>每个接收学生一条记录，标记已读/未读状态。
 *
 * <p>字段对照：
 * <pre>
 * id         → id         主键
 * noticeId   → notice_id  关联 sys_notice 主键
 * userId     → user_id    接收学生ID
 * isRead     → is_read    0未读 1已读
 * readTime   → read_time  阅读时间
 * </pre>
 */
@Data
public class SysUserNotice {

    private Long id;

    /** 关联通知ID */
    private Long noticeId;

    /** 接收学生ID */
    private Long userId;

    /** 0未读 1已读 */
    private Integer isRead;

    /** 0未确认 1已确认收到 */
    private Integer confirmStatus;

    /** 确认收到时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmTime;

    /** 0正常 1学生已逻辑删除 */
    private Integer isDeleted;

    /** 0普通 1学生置顶（置顶消息排在列表最前） */
    private Integer isTop;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // ===== 非表字段：学生端消息列表显示用 =====

    /** 通知标题（联表查询） */
    private String noticeTitle;

    /** 通知正文（联表查询） */
    private String noticeContent;

    /** 发送人姓名（联表查询） */
    private String sendUserName;

    /** 发送时间（联表查询） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sendTime;

    /** 关联考试ID（考试自动通知联表查询 sys_notice.target_exam_id，「立即前往考试」用） */
    private Long examId;
}
