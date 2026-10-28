package com.exam.exam_system.mapper;

import com.exam.exam_system.dto.QuickLinkItem;
import com.exam.exam_system.entity.UserQuickLink;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户自定义快捷入口 Mapper，对应 user_quick_link 表。
 *
 * <p>所有语句都以 user_id 为第一条件，保证各账号的配置互不可见、互不覆盖。
 *
 * <p>保存采用「先按 user_id 全删、再批量插入」的全量覆盖策略：单用户配置条数很少（上限 20 条），
 * 相比逐条 diff 更简单可靠，也不会出现残留记录导致的顺序错乱。
 */
@Mapper
public interface UserQuickLinkMapper {

    /** 某用户的全部自定义入口，按展示顺序升序（sort_no 相同再按 id） */
    @Select("SELECT id, user_id, label, route_path, icon, sort_no, create_time, update_time " +
            "FROM user_quick_link WHERE user_id = #{userId} ORDER BY sort_no ASC, id ASC")
    List<UserQuickLink> listByUser(@Param("userId") Long userId);

    /** 删除某用户的全部入口（保存前清空 / 恢复默认） */
    @Delete("DELETE FROM user_quick_link WHERE user_id = #{userId}")
    int deleteByUser(@Param("userId") Long userId);

    /**
     * 批量插入入口。
     * foreach 的 index 即数组下标，直接作为 sort_no 落库，保证首页展示顺序与前端一致。
     */
    @Insert("<script>" +
            "INSERT INTO user_quick_link(user_id, label, route_path, icon, sort_no) VALUES " +
            "<foreach collection='list' item='it' index='idx' separator=','>" +
            "(#{userId}, #{it.label}, #{it.path}, #{it.icon}, #{idx})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("userId") Long userId, @Param("list") List<QuickLinkItem> list);
}
