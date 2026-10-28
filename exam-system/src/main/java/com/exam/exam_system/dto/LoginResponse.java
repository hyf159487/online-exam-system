package com.exam.exam_system.dto;

import com.exam.exam_system.entity.SysMenu;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录响应体：返回给前端的 token + 用户基本信息 + 该用户的菜单权限树
 *
 * <p>RBAC 集成后，登录返回多两个字段：
 * <ul>
 *   <li>roleId  角色ID（关联 sys_role.id，1=学生 / 2=教师 / 3=管理员）</li>
 *   <li>menus   该角色拥有的菜单权限树，前端动态渲染侧边栏用</li>
 * </ul>
 *
 * <p>保留原 role 字段（int 0/1/2），保持与现有前端代码（role===0/1/2 判断）兼容。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private Long userId;
    private String username;
    private String realName;
    /** 兼容字段：0学生 / 1教师 / 2管理员（前端现有逻辑用这个） */
    private Integer role;
    /** RBAC 新增：角色ID（1/2/3，关联 sys_role.id） */
    private Long roleId;
    /** RBAC 新增：当前用户拥有的菜单权限树，前端动态渲染侧边栏 */
    private List<SysMenu> menus;
}
