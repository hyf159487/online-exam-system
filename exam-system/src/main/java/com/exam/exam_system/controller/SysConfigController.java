package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.entity.SysConfig;
import com.exam.exam_system.service.SysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统参数配置 Controller
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET  /api/config/list         全部参数列表（管理页面用）</li>
 *   <li>POST /api/config/batch-update 批量更新参数值（管理页面保存）</li>
 *   <li>GET  /api/config/value/{key}   按 key 取单个参数值（业务代码用，轻量接口）</li>
 * </ul>
 *
 * <p>权限：仅管理员可访问（AuthInterceptor 拦截）。
 */
@RestController
@RequestMapping("/api/config")
public class SysConfigController {

    @Autowired
    private SysConfigService sysConfigService;

    /** 全部参数列表 */
    @GetMapping("/list")
    public Result<List<SysConfig>> list() {
        return Result.success("查询成功", sysConfigService.list());
    }

    /**
     * 批量更新参数值
     * 请求体：[{ id: 1, configValue: "48" }, { id: 2, configValue: "30" }, ...]
     * 只允许改 configValue，其他字段不变
     */
    @PostMapping("/batch-update")
    public Result<Void> batchUpdate(@RequestBody List<SysConfig> list) {
        if (list == null || list.isEmpty()) {
            return Result.error(400, "参数列表不能为空");
        }
        for (SysConfig item : list) {
            if (item.getId() == null) {
                return Result.error(400, "参数ID不能为空");
            }
            if (item.getConfigValue() == null) {
                return Result.error(400, "参数值不能为空");
            }
            // 数字类型校验：不能输入负数或非数字
            SysConfig existing = sysConfigService.list().stream()
                    .filter(c -> c.getId().equals(item.getId()))
                    .findFirst().orElse(null);
            if (existing != null && "number".equals(existing.getConfigType())) {
                try {
                    int val = Integer.parseInt(item.getConfigValue());
                    if (val < 0) {
                        return Result.error(400, "参数「" + existing.getConfigName() + "」不能为负数");
                    }
                } catch (NumberFormatException e) {
                    return Result.error(400, "参数「" + existing.getConfigName() + "」必须是数字");
                }
            }
        }
        sysConfigService.batchUpdate(list);
        return Result.success("保存成功", null);
    }

    /** 按 key 取单个参数值（业务代码用，轻量接口，所有登录用户可访问） */
    @GetMapping("/value/{key}")
    public Result<SysConfig> getValue(@PathVariable String key) {
        SysConfig config = new SysConfig();
        config.setConfigKey(key);
        config.setConfigValue(sysConfigService.getValue(key));
        return Result.success("查询成功", config);
    }
}
