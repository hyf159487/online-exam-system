package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.SysMenu;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 菜单 Mapper，对应 sys_menu 表
 *
 * <p>查询模式：
 * <ul>
 *   <li>{@link #selectAll}               全部菜单（构建菜单树用，分配权限弹窗）</li>
 *   <li>{@link #selectAllByLayout}       按 layout 区分（admin / student）</li>
 *   <li>{@link #selectByRoleId}          按角色 ID 查该角色拥有的菜单（登录后渲染侧边栏）</li>
 *   <li>{@link #selectMenuIdsByRoleId}   只查 menu_id 列表（用于回显已勾选菜单）</li>
 * </ul>
 */
@Mapper
public interface SysMenuMapper {

    @Select("SELECT * FROM sys_menu ORDER BY sort_order ASC, id ASC")
    List<SysMenu> selectAll();

    @Select("SELECT * FROM sys_menu WHERE layout = #{layout} ORDER BY sort_order ASC, id ASC")
    List<SysMenu> selectAllByLayout(@Param("layout") String layout);

    /**
     * 按角色查菜单（JOIN sys_role_menu），登录后渲染侧边栏用
     * 排序按 sort_order 升序、id 升序，保证侧边栏顺序稳定
     */
    @Select("SELECT m.* FROM sys_menu m " +
            "INNER JOIN sys_role_menu rm ON rm.menu_id = m.id " +
            "WHERE rm.role_id = #{roleId} AND m.visible = 1 " +
            "ORDER BY m.sort_order ASC, m.id ASC")
    List<SysMenu> selectByRoleId(@Param("roleId") Long roleId);

    /** 只查 menu_id 列表（分配权限弹窗回显已勾选用） */
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);

    @Select("SELECT * FROM sys_menu WHERE id = #{id}")
    SysMenu selectById(@Param("id") Long id);

    @Insert("INSERT INTO sys_menu (parent_id, menu_name, route_path, icon, sort_order, visible, layout) " +
            "VALUES (#{parentId}, #{menuName}, #{routePath}, #{icon}, #{sortOrder}, #{visible}, #{layout})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysMenu menu);

    @Update("UPDATE sys_menu SET parent_id = #{parentId}, menu_name = #{menuName}, " +
            "route_path = #{routePath}, icon = #{icon}, sort_order = #{sortOrder}, " +
            "visible = #{visible}, layout = #{layout} WHERE id = #{id}")
    int update(SysMenu menu);

    @Delete("DELETE FROM sys_menu WHERE id = #{id}")
    int delete(@Param("id") Long id);
}
