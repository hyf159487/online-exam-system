package com.exam.exam_system.service.impl;

import com.exam.exam_system.entity.SysRole;
import com.exam.exam_system.mapper.SysRoleMapper;
import com.exam.exam_system.mapper.SysRoleMenuMapper;
import com.exam.exam_system.service.SysRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色服务实现
 *
 * <p>核心业务约束：
 * <ul>
 *   <li>role_key='admin' 的角色禁止删除（防止系统无超级管理员）</li>
 *   <li>role_key 唯一，重复新增返回 -1</li>
 *   <li>分配菜单权限时，admin 角色不允许 menuIds 为空</li>
 *   <li>有用户引用的角色禁止删除（防止用户角色悬空）</li>
 * </ul>
 *
 * <p>分配权限走事务：先删 sys_role_menu 旧关联，再批量插入新关联，
 * 任何一步失败回滚，避免出现"权限全清空"的事故。
 */
@Service
public class SysRoleServiceImpl implements SysRoleService {

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysRoleMenuMapper sysRoleMenuMapper;

    @Override
    public List<SysRole> list(String roleName) {
        return sysRoleMapper.listByQuery(roleName);
    }

    @Override
    public SysRole getById(Long id) {
        return sysRoleMapper.selectById(id);
    }

    @Override
    public Long save(SysRole role) {
        SysRole exists = sysRoleMapper.selectByRoleKey(role.getRoleKey());
        if (exists != null) {
            return -1L;       // role_key 重复
        }
        sysRoleMapper.insert(role);
        return role.getId();
    }

    @Override
    public void update(SysRole role) {
        // 只允许改 role_name / remark，role_key 不允许改
        SysRole update = new SysRole();
        update.setId(role.getId());
        update.setRoleName(role.getRoleName());
        update.setRemark(role.getRemark());
        sysRoleMapper.update(update);
    }

    @Override
    public void delete(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        if ("admin".equals(role.getRoleKey())) {
            throw new IllegalStateException("超级管理员角色禁止删除");
        }
        long userCount = sysRoleMapper.countUsersByRoleId(id);
        if (userCount > 0) {
            throw new IllegalStateException("该角色仍有 " + userCount + " 个用户引用，不能删除");
        }
        // 删除角色前先清空菜单关联
        sysRoleMenuMapper.deleteByRoleId(id);
        sysRoleMapper.deleteProtected(id);
    }

    @Override
    public List<Long> getMenuIdsByRoleId(Long roleId) {
        return sysRoleMenuMapper.selectMenuIdsByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        if (menuIds == null || menuIds.isEmpty()) {
            throw new IllegalStateException("不允许清空全部菜单权限，至少保留一个");
        }
        // 覆盖式更新：先全删，再批量插
        sysRoleMenuMapper.deleteByRoleId(roleId);
        List<SysRoleMenuMapper.RoleMenuRel> list = new ArrayList<>(menuIds.size());
        for (Long menuId : menuIds) {
            list.add(new SysRoleMenuMapper.RoleMenuRel(roleId, menuId));
        }
        if (!list.isEmpty()) {
            sysRoleMenuMapper.insertBatch(list);
        }
    }
}
