package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.SysNotice;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 通知主表 Mapper，对应 sys_notice
 */
@Mapper
public interface NoticeMapper {

    /** 插入一条通知主记录，回填自增 id */
    @Insert("INSERT INTO sys_notice(title, content, receive_type, target_class_ids, target_exam_id, " +
            "send_user_id, send_user_name) " +
            "VALUES (#{title}, #{content}, #{receiveType}, #{targetClassIds}, #{targetExamId}, " +
            "#{sendUserId}, #{sendUserName})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysNotice notice);

    /** 按 ID 查单条通知（兼容 is_deleted 为 NULL 的旧数据） */
    @Select("SELECT n.*, " +
            "(SELECT COUNT(*) FROM sys_user_notice un WHERE un.notice_id = n.id AND (un.is_deleted = 0 OR un.is_deleted IS NULL)) AS receiveCount " +
            "FROM sys_notice n WHERE n.id = #{id} AND (n.is_deleted = 0 OR n.is_deleted IS NULL)")
    SysNotice selectById(@Param("id") Long id);

    /** 逻辑删除通知 */
    @Update("UPDATE sys_notice SET is_deleted = 1 WHERE id = #{id}")
    int logicDelete(@Param("id") Long id);

    /** 编辑通知标题与内容，并刷新发送时间（重新发布时间） */
    @Update("UPDATE sys_notice SET title = #{title}, content = #{content}, send_time = NOW() WHERE id = #{id}")
    int updateContent(@Param("id") Long id, @Param("title") String title, @Param("content") String content);

    /** 重新通知：重置该通知所有接收人的已读与确认状态（需重新阅读并确认），已删除的接收记录不处理 */
    @Update("UPDATE sys_user_notice SET is_read = 0, confirm_status = 0, confirm_time = NULL " +
            "WHERE notice_id = #{noticeId} AND (is_deleted = 0 OR is_deleted IS NULL)")
    int resetAllRead(@Param("noticeId") Long noticeId);

    /** 查询某用户的角色（用于教师查看管理员通知的权限判断） */
    @Select("SELECT role FROM sys_user WHERE id = #{userId}")
    Integer selectUserRole(@Param("userId") Long userId);

    /**
     * 分页查询通知列表（@Select + script 动态 SQL，兼容所有 MyBatis 版本）
     * 教师：查自己发的 + 管理员发的（管理员通知教师端只读）
     * 管理员：查全部
     */
    @Select("<script>" +
            "SELECT n.*, " +
            "(SELECT COUNT(*) FROM sys_user_notice un WHERE un.notice_id = n.id " +
            "  AND (un.is_deleted = 0 OR un.is_deleted IS NULL)) AS receiveCount, " +
            "(SELECT COUNT(*) FROM sys_user_notice uc WHERE uc.notice_id = n.id AND uc.confirm_status = 1 " +
            "  AND (uc.is_deleted = 0 OR uc.is_deleted IS NULL)) AS confirmedCount " +
            "FROM sys_notice n " +
            "WHERE (n.is_deleted = 0 OR n.is_deleted IS NULL) " +
            "<if test='role != null and role == 1'>" +
            "  AND (n.send_user_id = #{userId} " +
            "       OR EXISTS (SELECT 1 FROM sys_user su WHERE su.id = n.send_user_id AND su.role = 2)) " +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'> AND n.title LIKE CONCAT('%', #{keyword}, '%') </if>" +
            "ORDER BY n.send_time DESC " +
            "LIMIT #{offset}, #{pageSize}" +
            "</script>")
    List<SysNotice> listPage(@Param("offset") int offset,
                             @Param("pageSize") int pageSize,
                             @Param("userId") Long userId,
                             @Param("role") Integer role,
                             @Param("keyword") String keyword);

    /** 查询总数（配合分页，@Select + script 动态 SQL） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM sys_notice n " +
            "WHERE (n.is_deleted = 0 OR n.is_deleted IS NULL) " +
            "<if test='role != null and role == 1'>" +
            "  AND (n.send_user_id = #{userId} " +
            "       OR EXISTS (SELECT 1 FROM sys_user su WHERE su.id = n.send_user_id AND su.role = 2)) " +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'> AND n.title LIKE CONCAT('%', #{keyword}, '%') </if>" +
            "</script>")
    long countPage(@Param("userId") Long userId,
                   @Param("role") Integer role,
                   @Param("keyword") String keyword);
}
