package com.exam.exam_system.controller;

import com.exam.exam_system.annotation.OperationLog;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.QuickLinkItem;
import com.exam.exam_system.dto.QuickLinkSaveRequest;
import com.exam.exam_system.entity.UserQuickLink;
import com.exam.exam_system.service.UserQuickLinkService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页「快捷入口」自定义配置 Controller。
 *
 * <p>入口来源是用户自己的菜单权限，也支持手填名称 + 路由；配置落在 user_quick_link 表、
 * 跟账号走 —— 换浏览器 / 换电脑登录后依然是自己挑的那几个入口。
 *
 * <p>路径 /api/user/** 不在 AuthInterceptor 的专项角色规则内，所有已登录用户都只能读写自己的配置
 * （userId 从 request 属性取，见 AuthInterceptor 第 5 步）；
 * 教师端 / 管理员端（Dashboard.vue）与学生端（StudentDashboard.vue）首页均开放「自定义」按钮。
 */
@RestController
@RequestMapping("/api/user/quick-links")
public class UserQuickLinkController {

    @Autowired
    private UserQuickLinkService userQuickLinkService;

    /** 我的快捷入口；未自定义过返回空数组，前端回落到系统默认入口 */
    @GetMapping
    public Result<List<UserQuickLink>> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success("查询成功", userQuickLinkService.listByUser(userId));
    }

    /** 保存我的快捷入口（全量覆盖，body：{ "links": [ { "label": "...", "path": "...", "icon": "..." } ] }） */
    @OperationLog(module = "首页配置", type = "修改", description = "保存自定义快捷入口")
    @PostMapping
    public Result<Void> save(@RequestBody(required = false) QuickLinkSaveRequest req, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<QuickLinkItem> links = req == null ? null : req.getLinks();
        userQuickLinkService.save(userId, links);
        return Result.success("快捷入口已保存", null);
    }

    /** 恢复默认：清空我的自定义配置，首页回到系统内置入口 */
    @OperationLog(module = "首页配置", type = "重置", description = "恢复默认快捷入口")
    @DeleteMapping
    public Result<Void> reset(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        userQuickLinkService.reset(userId);
        return Result.success("已恢复默认快捷入口", null);
    }
}
