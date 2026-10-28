package com.exam.exam_system.controller;

import com.exam.exam_system.common.Result;
import com.exam.exam_system.entity.SysDictItem;
import com.exam.exam_system.entity.SysDictType;
import com.exam.exam_system.service.DictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典管理 Controller（管理员配置页 + 业务只读接口）
 *
 * <p>接口清单：
 * <ul>
 *   <li>类型 CRUD：GET/POST/PUT/DELETE /api/dict/types</li>
 *   <li>子项 CRUD：GET/POST/PUT/DELETE /api/dict/items</li>
 *   <li>业务只读：GET /api/dict/items/code/{typeCode} — 按编码取启用子项</li>
 * </ul>
 *
 * <p>权限：
 * <ul>
 *   <li>GET /api/dict/**        — 所有登录用户可访问（业务下拉框用）</li>
 *   <li>POST/PUT/DELETE /api/dict/** — 仅管理员（role=2）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/dict")
public class DictController {

    @Autowired
    private DictService dictService;

    // ===== 字典类型 =====

    @GetMapping("/types")
    public Result<List<SysDictType>> listTypes(
            @RequestParam(required = false) String typeName) {
        return Result.success("查询成功", dictService.listTypes(typeName));
    }

    @GetMapping("/types/{id}")
    public Result<SysDictType> getTypeById(@PathVariable Long id) {
        SysDictType type = dictService.getTypeById(id);
        if (type == null) return Result.error(404, "字典类型不存在");
        return Result.success("查询成功", type);
    }

    @PostMapping("/types")
    public Result<Long> saveType(@RequestBody SysDictType type) {
        if (type.getTypeName() == null || type.getTypeName().isBlank()) {
            return Result.error(400, "字典类型名称不能为空");
        }
        if (type.getTypeCode() == null || type.getTypeCode().isBlank()) {
            return Result.error(400, "字典编码不能为空");
        }
        Long id = dictService.saveType(type);
        if (id == -1L) return Result.error(400, "字典编码已存在");
        return Result.success("新增成功", id);
    }

    @PutMapping("/types")
    public Result<Void> updateType(@RequestBody SysDictType type) {
        if (type.getId() == null) return Result.error(400, "ID不能为空");
        if (type.getTypeName() == null || type.getTypeName().isBlank()) {
            return Result.error(400, "字典类型名称不能为空");
        }
        dictService.updateType(type);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/types/{id}")
    public Result<Void> deleteType(@PathVariable Long id) {
        try {
            dictService.deleteType(id);
            return Result.success("删除成功", null);
        } catch (IllegalStateException e) {
            return Result.error(400, e.getMessage());
        }
    }

    // ===== 字典子项 =====

    @GetMapping("/items")
    public Result<List<SysDictItem>> listItems(@RequestParam Long typeId) {
        return Result.success("查询成功", dictService.listItemsByTypeId(typeId));
    }

    @GetMapping("/items/{id}")
    public Result<SysDictItem> getItemById(@PathVariable Long id) {
        SysDictItem item = dictService.getItemById(id);
        if (item == null) return Result.error(404, "字典子项不存在");
        return Result.success("查询成功", item);
    }

    /**
     * 业务最常用接口：按字典编码取启用的子项列表
     * GET /api/dict/items/code/{typeCode}
     * 前端业务页面（题库/试卷/考试等）下拉框用这个接口动态获取选项
     */
    @GetMapping("/items/code/{typeCode}")
    public Result<List<SysDictItem>> getItemsByCode(@PathVariable String typeCode) {
        return Result.success("查询成功", dictService.getEnabledItemsByCode(typeCode));
    }

    @PostMapping("/items")
    public Result<Long> saveItem(@RequestBody SysDictItem item) {
        if (item.getTypeId() == null) return Result.error(400, "字典类型ID不能为空");
        if (item.getItemLabel() == null || item.getItemLabel().isBlank()) {
            return Result.error(400, "显示标签不能为空");
        }
        if (item.getItemValue() == null || item.getItemValue().isBlank()) {
            return Result.error(400, "存储值不能为空");
        }
        Long id = dictService.saveItem(item);
        if (id == -1L) return Result.error(400, "同一类型下已存在相同存储值的子项");
        return Result.success("新增成功", id);
    }

    @PutMapping("/items")
    public Result<Void> updateItem(@RequestBody SysDictItem item) {
        if (item.getId() == null) return Result.error(400, "ID不能为空");
        try {
            dictService.updateItem(item);
            return Result.success("更新成功", null);
        } catch (IllegalStateException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        dictService.deleteItem(id);
        return Result.success("删除成功", null);
    }
}
