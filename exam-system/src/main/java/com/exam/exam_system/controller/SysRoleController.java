package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.entity.SysRole;
import com.exam.exam_system.service.SysRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色 Controller（管理员专属 RBAC 配置页面）
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET   /api/role/list                  角色列表（支持名称模糊）</li>
 *   <li>GET   /api/role/{id}                   查单条</li>
 *   <li>POST  /api/role                        新增角色（role_key 唯一）</li>
 *   <li>PUT   /api/role                        编辑角色（仅 name/remark）</li>
 *   <li>DELETE /api/role/{id}                  删除角色（admin 受保护禁删）</li>
 *   <li>GET   /api/role/{id}/menus             取角色已勾选菜单 ID（回显）</li>
 *   <li>POST  /api/role/{id}/menus             分配菜单权限（覆盖式 + 事务）</li>
 * </ul>
 *
 * <p>所有接口由 AuthInterceptor 限制仅管理员(role_id=3) 可访问。
 */
@RestController
@RequestMapping("/api/role")
public class SysRoleController {

    @Autowired
    private SysRoleService sysRoleService;

    /** 角色列表（支持名称模糊查询） */
    @GetMapping("/list")
    public Result<List<SysRole>> list(@RequestParam(required = false) String roleName) {
        return Result.success("查询成功", sysRoleService.list(roleName));
    }

    /** 查单条 */
    @GetMapping("/{id}")
    public Result<SysRole> getById(@PathVariable Long id) {
        SysRole role = sysRoleService.getById(id);
        if (role == null) {
            return Result.error(404, "角色不存在");
        }
        return Result.success("查询成功", role);
    }

    /** 新增角色 */
    @PostMapping
    public Result<Long> save(@RequestBody SysRole role) {
        if (role.getRoleName() == null || role.getRoleName().isBlank()) {
            return Result.error(400, "角色名称不能为空");
        }
        if (role.getRoleKey() == null || role.getRoleKey().isBlank()) {
            return Result.error(400, "角色标识不能为空");
        }
        Long id = sysRoleService.save(role);
        if (id == -1L) {
            return Result.error(400, "角色标识已存在");
        }
        return Result.success("新增成功", id);
    }

    /** 编辑角色（只允许改 name/remark，不允许改 role_key） */
    @PutMapping
    public Result<Void> update(@RequestBody SysRole role) {
        if (role.getId() == null) {
            return Result.error(400, "角色ID不能为空");
        }
        if (role.getRoleName() == null || role.getRoleName().isBlank()) {
            return Result.error(400, "角色名称不能为空");
        }
        sysRoleService.update(role);
        return Result.success("更新成功", null);
    }

    /**
     * 删除角色
     * <ul>
     *   <li>role_key='admin' 禁止删除</li>
     *   <li>有用户引用禁止删除</li>
     * </ul>
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            sysRoleService.delete(id);
            return Result.success("删除成功", null);
        } catch (IllegalStateException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 取角色已勾选的菜单 ID 列表（分配弹窗回显） */
    @GetMapping("/{id}/menus")
    public Result<List<Long>> getRoleMenus(@PathVariable Long id) {
        return Result.success("查询成功", sysRoleService.getMenuIdsByRoleId(id));
    }

    /**
     * 给角色分配菜单权限
     * 请求体：菜单 ID 数组，如 [1, 2, 3, 10, 11, ...]
     * 覆盖式更新（先删后插），事务保证不会出现权限空档
     */
    @PostMapping("/{id}/menus")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        try {
            sysRoleService.assignMenus(id, menuIds);
            return Result.success("分配成功", null);
        } catch (IllegalStateException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
