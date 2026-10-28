package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.SysUserNotice;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户通知接收记录 Mapper，对应 sys_user_notice
 */
@Mapper
public interface UserNoticeMapper {

    /**
     * 批量插入用户通知接收记录
     * 使用 script 标签 + foreach 循环插入
     */
    @Insert("<script>" +
            "INSERT INTO sys_user_notice (notice_id, user_id) VALUES " +
            "<foreach collection='userIdList' item='userId' separator=','>" +
            "(#{noticeId}, #{userId})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("noticeId") Long noticeId,
                    @Param("userIdList") List<Long> userIdList);

    /** 逻辑删除通知时，一并删除关联的用户通知记录 */
    @Delete("DELETE FROM sys_user_notice WHERE notice_id = #{noticeId}")
    int deleteByNoticeId(@Param("noticeId") Long noticeId);

    /** 查询某用户未读消息数（排除学生已逻辑删除的，兼容旧数据 is_deleted 为 NULL） */
    @Select("SELECT COUNT(*) FROM sys_user_notice un " +
            "JOIN sys_notice n ON un.notice_id = n.id " +
            "WHERE un.user_id = #{userId} AND un.is_read = 0 " +
            "  AND (un.is_deleted = 0 OR un.is_deleted IS NULL) AND n.is_deleted = 0")
    int countUnread(@Param("userId") Long userId);

    /**
     * 查询某用户收到的消息列表（含通知详情）
     * 置顶消息优先，同级按发送时间倒序，最多取最近 50 条
     * 排除学生已逻辑删除的记录
     */
    @Select("SELECT un.id, un.notice_id AS noticeId, un.user_id AS userId, " +
            "un.is_read AS isRead, un.read_time AS readTime, un.create_time AS createTime, " +
            "un.confirm_status AS confirmStatus, un.confirm_time AS confirmTime, un.is_top AS isTop, " +
            "n.title AS noticeTitle, n.content AS noticeContent, " +
            "n.send_user_name AS sendUserName, n.send_time AS sendTime, n.target_exam_id AS examId " +
            "FROM sys_user_notice un " +
            "JOIN sys_notice n ON un.notice_id = n.id " +
            "WHERE un.user_id = #{userId} AND (un.is_deleted = 0 OR un.is_deleted IS NULL) AND n.is_deleted = 0 " +
            "ORDER BY un.is_top DESC, n.send_time DESC LIMIT #{limit}")
    List<SysUserNotice> listByUser(@Param("userId") Long userId, @Param("limit") int limit);

    /**
     * 分页查询某用户收到的消息（消息通知页面用）
     * 置顶消息优先，同级按发送时间倒序，排除已逻辑删除
     */
    @Select("SELECT un.id, un.notice_id AS noticeId, un.user_id AS userId, " +
            "un.is_read AS isRead, un.read_time AS readTime, un.create_time AS createTime, " +
            "un.confirm_status AS confirmStatus, un.confirm_time AS confirmTime, un.is_top AS isTop, " +
            "n.title AS noticeTitle, n.content AS noticeContent, " +
            "n.send_user_name AS sendUserName, n.send_time AS sendTime, n.target_exam_id AS examId " +
            "FROM sys_user_notice un " +
            "JOIN sys_notice n ON un.notice_id = n.id " +
            "WHERE un.user_id = #{userId} AND (un.is_deleted = 0 OR un.is_deleted IS NULL) AND n.is_deleted = 0 " +
            "ORDER BY un.is_top DESC, n.send_time DESC LIMIT #{offset}, #{pageSize}")
    List<SysUserNotice> listByUserPage(@Param("userId") Long userId,
                                       @Param("offset") int offset,
                                       @Param("pageSize") int pageSize);

    /** 统计某用户可见消息总数（用于分页 total，兼容旧数据 is_deleted 为 NULL） */
    @Select("SELECT COUNT(*) FROM sys_user_notice un " +
            "JOIN sys_notice n ON un.notice_id = n.id " +
            "WHERE un.user_id = #{userId} AND (un.is_deleted = 0 OR un.is_deleted IS NULL) AND n.is_deleted = 0")
    int countByUser(@Param("userId") Long userId);

    /**
     * 标记某条通知已读（兼容旧数据 is_deleted 为 NULL）
     */
    @Update("UPDATE sys_user_notice SET is_read = 1, read_time = NOW() " +
            "WHERE notice_id = #{noticeId} AND user_id = #{userId} AND is_read = 0 " +
            "  AND (is_deleted = 0 OR is_deleted IS NULL)")
    int markRead(@Param("noticeId") Long noticeId, @Param("userId") Long userId);

    /**
     * 全部标记已读（排除已逻辑删除的，兼容旧数据 is_deleted 为 NULL）
     */
    @Update("UPDATE sys_user_notice SET is_read = 1, read_time = NOW() " +
            "WHERE user_id = #{userId} AND is_read = 0 AND (is_deleted = 0 OR is_deleted IS NULL) " +
            "AND notice_id IN (SELECT id FROM sys_notice WHERE is_deleted = 0)")
    int markAllRead(@Param("userId") Long userId);

    /**
     * 学生逻辑删除自己的一条消息（按 user_notice 主键 id 删，且校验 user_id 防越权）
     */
    @Update("UPDATE sys_user_notice SET is_deleted = 1 " +
            "WHERE id = #{id} AND user_id = #{userId} AND (is_deleted = 0 OR is_deleted IS NULL)")
    int logicDelete(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 学生确认收到消息（校验归属防越权，已确认的幂等不重复更新）
     */
    @Update("UPDATE sys_user_notice SET confirm_status = 1, confirm_time = NOW() " +
            "WHERE notice_id = #{noticeId} AND user_id = #{userId} AND confirm_status = 0 " +
            "  AND (is_deleted = 0 OR is_deleted IS NULL)")
    int confirm(@Param("noticeId") Long noticeId, @Param("userId") Long userId);

    /** 统计某条通知已确认收到的人数（发布者确认统计用） */
    @Select("SELECT COUNT(*) FROM sys_user_notice " +
            "WHERE notice_id = #{noticeId} AND confirm_status = 1 AND (is_deleted = 0 OR is_deleted IS NULL)")
    int countConfirmed(@Param("noticeId") Long noticeId);

    /** 校验消息归属：学生只能操作发给自己的消息 */
    @Select("SELECT COUNT(*) FROM sys_user_notice " +
            "WHERE notice_id = #{noticeId} AND user_id = #{userId} AND (is_deleted = 0 OR is_deleted IS NULL)")
    int countOwned(@Param("noticeId") Long noticeId, @Param("userId") Long userId);

    /** 按通知+用户查询接收记录（确认幂等回查用） */
    @Select("SELECT * FROM sys_user_notice " +
            "WHERE notice_id = #{noticeId} AND user_id = #{userId} AND (is_deleted = 0 OR is_deleted IS NULL)")
    SysUserNotice getByNoticeAndUser(@Param("noticeId") Long noticeId, @Param("userId") Long userId);

    /**
     * 学生把已读消息重新标记为未读（校验归属防越权）
     */
    @Update("UPDATE sys_user_notice SET is_read = 0, read_time = NULL " +
            "WHERE notice_id = #{noticeId} AND user_id = #{userId} AND is_read = 1 " +
            "  AND (is_deleted = 0 OR is_deleted IS NULL)")
    int markUnread(@Param("noticeId") Long noticeId, @Param("userId") Long userId);

    /**
     * 学生置顶 / 取消置顶自己的消息（按 user_notice 主键 id，校验 user_id 防越权）
     */
    @Update("UPDATE sys_user_notice SET is_top = #{isTop} " +
            "WHERE id = #{id} AND user_id = #{userId} AND (is_deleted = 0 OR is_deleted IS NULL)")
    int setTop(@Param("id") Long id, @Param("userId") Long userId, @Param("isTop") Integer isTop);
}
