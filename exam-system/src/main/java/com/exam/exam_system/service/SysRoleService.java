package com.exam.exam_system.service;

import com.exam.exam_system.entity.SysRole;

import java.util.List;

/**
 * 角色服务
 *
 * <p>核心方法：
 * <ul>
 *   <li>{@link #list}                分页/模糊查询角色列表</li>
 *   <li>{@link #getById}             查单条</li>
 *   <li>{@link #save}               新增角色（role_key 唯一校验）</li>
 *   <li>{@link #update}             编辑角色（不允许改 role_key）</li>
 *   <li>{@link #delete}             删除角色（admin 受保护禁删；有用户引用禁删）</li>
 *   <li>{@link #getMenuIdsByRoleId} 取该角色已勾选的菜单ID（分配弹窗回显）</li>
 *   <li>{@link #assignMenus}        分配菜单权限（先删后批量插，admin 至少保留全部）</li>
 * </ul>
 */
public interface SysRoleService {

    /** 模糊查询角色列表（不分页，固定 3 个角色） */
    List<SysRole> list(String roleName);

    SysRole getById(Long id);

    /** 新增角色，role_key 重复返回 -1 */
    Long save(SysRole role);

    /** 编辑角色，仅允许改 role_name / remark */
    void update(SysRole role);

    /**
     * 删除角色
     * <ul>
     *   <li>role_key='admin' 禁止删除，抛异常</li>
     *   <li>有用户引用该角色禁止删除，抛异常</li>
     * </ul>
     */
    void delete(Long id);

    /** 取角色已勾选的菜单 ID 列表（分配弹窗回显用） */
    List<Long> getMenuIdsByRoleId(Long roleId);

    /**
     * 给角色分配菜单权限
     * <p>覆盖式：先删该角色所有 sys_role_menu，再批量插入新的
     * <p>保护规则：admin 角色至少要保留全部菜单，menuIds 为空时拒绝
     *
     * @param roleId  角色 ID
     * @param menuIds 菜单 ID 列表（叶子菜单 ID + 父菜单 ID 都要传）
     */
    void assignMenus(Long roleId, List<Long> menuIds);
}
