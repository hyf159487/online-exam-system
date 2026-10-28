package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.entity.SysMenu;
import com.exam.exam_system.service.SysMenuService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单 Controller
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET  /api/menu/tree                 全部菜单树（分配权限弹窗展示）</li>
 *   <li>GET  /api/menu/tree?layout=student  按 layout 区分的菜单树</li>
 *   <li>GET  /api/menu/my                    当前登录用户的菜单树（动态渲染侧边栏）</li>
 *   <li>POST /api/menu                      新增菜单</li>
 *   <li>PUT  /api/menu                       编辑菜单</li>
 *   <li>DELETE /api/menu/{id}                删除菜单</li>
 * </ul>
 *
 * <p>GET 接口管理员可见，POST/PUT/DELETE 由 AuthInterceptor 限制仅管理员。
 */
@RestController
@RequestMapping("/api/menu")
public class SysMenuController {

    @Autowired
    private SysMenuService sysMenuService;

    /**
     * 全部菜单树 / 按 layout 区分的菜单树
     * 不传 layout：返回全部菜单树（分配权限弹窗用）
     * 传 layout=admin/student：只返回该 layout 的菜单树
     */
    @GetMapping("/tree")
    public Result<List<SysMenu>> tree(
            @RequestParam(required = false) String layout) {
        if (layout == null || layout.isBlank()) {
            return Result.success("查询成功", sysMenuService.listAllTree());
        }
        return Result.success("查询成功", sysMenuService.listTreeByLayout(layout));
    }

    /**
     * 当前登录用户的菜单树
     * <p>从 request attribute 取 userId / role_id（AuthInterceptor 已注入）
     * <p>注意：AuthInterceptor 注入的是 role（int 0/1/2）和 userId，
     * role_id 在 sys_user 表里。这里通过 role 反推 role_id = role + 1。
     * <p>更稳妥的做法是登录时把 roleId 也写入 request attribute，
     * 这里为最小改动暂时反推。后续可优化 AuthInterceptor 直接注入 roleId。
     */
    @GetMapping("/my")
    public Result<List<SysMenu>> myMenus(HttpServletRequest request) {
        // role_id = sys_user.role + 1（学生0→1，教师1→2，管理员2→3）
        // 优先用 role_id（如果 AuthInterceptor 已注入）；否则 fallback 用 role+1
        Object roleIdAttr = request.getAttribute("roleId");
        Long roleId;
        if (roleIdAttr instanceof Long) {
            roleId = (Long) roleIdAttr;
        } else if (roleIdAttr instanceof Number) {
            roleId = ((Number) roleIdAttr).longValue();
        } else {
            // fallback：从 role (int 0/1/2) 反推
            Object roleAttr = request.getAttribute("role");
            if (roleAttr == null) {
                return Result.error(401, "未登录");
            }
            int role = ((Number) roleAttr).intValue();
            roleId = (long) (role + 1);
        }
        return Result.success("查询成功", sysMenuService.listTreeByRoleId(roleId));
    }

    /** 新增菜单 */
    @PostMapping
    public Result<Long> save(@RequestBody SysMenu menu) {
        if (menu.getMenuName() == null || menu.getMenuName().isBlank()) {
            return Result.error(400, "菜单名称不能为空");
        }
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        if (menu.getSortOrder() == null) {
            menu.setSortOrder(0);
        }
        if (menu.getVisible() == null) {
            menu.setVisible(1);
        }
        if (menu.getLayout() == null || menu.getLayout().isBlank()) {
            menu.setLayout("admin");
        }
        return Result.success("新增成功", sysMenuService.save(menu));
    }

    /** 编辑菜单 */
    @PutMapping
    public Result<Void> update(@RequestBody SysMenu menu) {
        if (menu.getId() == null) {
            return Result.error(400, "菜单ID不能为空");
        }
        sysMenuService.update(menu);
        return Result.success("更新成功", null);
    }

    /** 删除菜单 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysMenuService.delete(id);
        return Result.success("删除成功", null);
    }
}
