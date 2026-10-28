package com.exam.exam_system.service;

import com.exam.exam_system.entity.SysMenu;

import java.util.List;

/**
 * 菜单服务
 *
 * <p>核心方法：
 * <ul>
 *   <li>{@link #listAllTree}        全部菜单树（分配权限弹窗展示）</li>
 *   <li>{@link #listTreeByLayout}   按 layout 区分的菜单树（学生/教师端）</li>
 *   <li>{@link #listTreeByRoleId}   按角色 ID 查菜单树（登录后渲染侧边栏）</li>
 *   <li>{@link #getMenuIdsByRoleId} 查角色已勾选菜单 ID 列表（分配弹窗回显）</li>
 * </ul>
 *
 * <p>树构建逻辑：DB 拿到 flat list → 按 parentId 组装成树 → 同级按 sortOrder 升序。
 */
public interface SysMenuService {

    /** 全部菜单树（admin 分配权限弹窗用） */
    List<SysMenu> listAllTree();

    /** 按 layout 区分的菜单树（'admin'=教师端 / 'student'=学生端） */
    List<SysMenu> listTreeByLayout(String layout);

    /**
     * 按角色 ID 查菜单树
     * <p>登录后渲染侧边栏用：根据用户的 role_id 查该角色拥有的菜单，
     * 按 layout（admin/student）分别返回，前端按角色分流取对应 list。
     *
     * @param roleId 角色 ID
     * @return 该角色拥有的菜单树（已按 sort_order 排序）
     */
    List<SysMenu> listTreeByRoleId(Long roleId);

    /** 取角色已勾选的菜单 ID 列表（分配弹窗回显） */
    List<Long> getMenuIdsByRoleId(Long roleId);

    SysMenu getById(Long id);

    Long save(SysMenu menu);

    void update(SysMenu menu);

    void delete(Long id);
}
