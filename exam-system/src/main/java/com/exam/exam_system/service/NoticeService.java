package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.NoticeSendRequest;
import com.exam.exam_system.entity.SysNotice;
import com.exam.exam_system.entity.SysUserNotice;

import java.util.List;

/**
 * 系统通知 Service
 */
public interface NoticeService {

    /**
     * 发布通知
     */
    Long sendNotice(NoticeSendRequest request, Long sendUserId, String sendUserName, Integer role);

    /**
     * 通知记录列表（教师看自己发的 + 管理员发的，管理员看全部）
     */
    PageResult<SysNotice> listNotices(int pageNum, int pageSize, Long userId, Integer role, String keyword);

    /**
     * 通知详情
     */
    SysNotice getNoticeDetail(Long id);

    /**
     * 查询某用户角色（教师查看管理员通知详情的权限判断用）
     */
    Integer getSenderRole(Long userId);

    /**
     * 编辑通知并重新发布：更新标题/内容，发送时间刷新为当前时间，
     * 并重置全部接收人的已读状态（未读红点重新出现，实现重新通知）
     * 权限：管理员可编辑全部；教师只能编辑自己发送的
     */
    void updateAndResend(Long id, String title, String content, Long userId, Integer role);

    /**
     * 发布考试自动通知全体学生（系统自动行为，不受"教师不能发全体学生"限制）
     * 生成 receiveType=5 的通知，逐人写入 sys_user_notice
     *
     * @param exam          已发布的考试（含考试名称/时间/时长/总分）
     * @param publisherId   发布操作人 userId（通知记录归属，教师/管理员可在通知记录中管理）
     * @param publisherName 发布操作人姓名
     */
    void sendExamNotice(com.exam.exam_system.entity.Exam exam, Long publisherId, String publisherName);

    /**
     * 学生确认收到消息（幂等：已确认的重复确认直接返回成功）
     * 校验消息归属，学生只能确认发给自己的消息
     *
     * @return 确认时间（字符串 yyyy-MM-dd HH:mm:ss）
     */
    String confirmUserNotice(Long userId, Long noticeId);

    /**
     * 学生把已读消息重新标记为未读（校验归属）
     */
    void markUserNoticeUnread(Long userId, Long noticeId);

    /**
     * 学生置顶 / 取消置顶自己的消息（校验归属）
     *
     * @param userNoticeId sys_user_notice 主键
     * @param top          true 置顶 / false 取消置顶
     */
    void setUserNoticeTop(Long userId, Long userNoticeId, boolean top);

    /**
     * 删除通知（逻辑删除）
     */
    void deleteNotice(Long id, Long userId, Integer role);

    // ========== 学生端接口 ==========

    /**
     * 查询用户未读消息数
     */
    int countUnread(Long userId);

    /**
     * 查询用户收到的消息列表
     */
    List<SysUserNotice> listUserNotices(Long userId, int limit);

    /**
     * 标记某条通知已读
     */
    void markRead(Long noticeId, Long userId);

    /**
     * 全部标记已读
     */
    void markAllRead(Long userId);

    /**
     * 分页查询某用户收到的消息（消息通知页面用）
     * 排除学生已逻辑删除的记录，按发送时间倒序
     */
    PageResult<SysUserNotice> listUserNoticesPage(Long userId, int pageNum, int pageSize);

    /**
     * 学生逻辑删除自己的一条消息（按 user_notice 主键 id 删）
     * 校验 user_id 防止越权删除他人消息
     */
    void logicDeleteUserNotice(Long id, Long userId);
}
