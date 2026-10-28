package com.exam.exam_system.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 角色-菜单关联 Mapper，对应 sys_role_menu 表
 *
 * <p>只提供 INSERT/DELETE/SELECT，不提供 UPDATE，
 * 因为分配权限时是「先按 role_id 全删，再批量插入」的覆盖式更新。
 */
@Mapper
public interface SysRoleMenuMapper {

    /** 批量插入角色-菜单关联 */
    @Insert("<script>" +
            "INSERT INTO sys_role_menu (role_id, menu_id) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.roleId}, #{item.menuId})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<RoleMenuRel> list);

    /** 按角色 ID 删除所有关联（分配权限前先清空） */
    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    /** 查询某角色已勾选的菜单 ID 列表（分配权限弹窗回显） */
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);

    /** 查询某角色已勾选的菜单数量（admin 校验用：不允许清空全部） */
    @Select("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = #{roleId}")
    long countByRoleId(@Param("roleId") Long roleId);

    /** 角色-菜单关联记录（用于批量插入） */
    class RoleMenuRel {
        private Long roleId;
        private Long menuId;

        public RoleMenuRel() {}

        public RoleMenuRel(Long roleId, Long menuId) {
            this.roleId = roleId;
            this.menuId = menuId;
        }

        public Long getRoleId() { return roleId; }
        public void setRoleId(Long roleId) { this.roleId = roleId; }
        public Long getMenuId() { return menuId; }
        public void setMenuId(Long menuId) { this.menuId = menuId; }
    }
}
