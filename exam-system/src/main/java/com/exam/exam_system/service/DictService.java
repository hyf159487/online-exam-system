package com.exam.exam_system.service;

import com.exam.exam_system.entity.SysDictItem;
import com.exam.exam_system.entity.SysDictType;

import java.util.List;

/**
 * 字典服务
 *
 * <p>核心方法：
 * <ul>
 *   <li>类型 CRUD：listType / getTypeById / saveType / updateType / deleteType</li>
 *   <li>子项 CRUD：listItemsByType / getItemById / saveItem / updateItem / deleteItem</li>
 *   <li>业务接口：getEnabledItemsByCode — 按字典编码取启用的子项（前端下拉框用）</li>
 * </ul>
 *
 * <p>保护规则：
 * <ul>
 *   <li>is_builtin=1 的字典类型禁止删除</li>
 *   <li>字典类型下有子项时禁止删除（防止孤儿数据）</li>
 *   <li>同一类型下 item_value 不允许重复</li>
 * </ul>
 */
public interface DictService {

    // ===== 字典类型 =====
    List<SysDictType> listTypes(String typeName);

    SysDictType getTypeById(Long id);

    /** 新增字典类型，typeCode 重复返回 -1 */
    Long saveType(SysDictType type);

    /** 编辑字典类型（仅允许改 typeName/remark） */
    void updateType(SysDictType type);

    /** 删除字典类型：内置禁删；有子项禁删 */
    void deleteType(Long id);

    // ===== 字典子项 =====
    List<SysDictItem> listItemsByTypeId(Long typeId);

    SysDictItem getItemById(Long id);

    /** 新增字典子项，同类型下 itemValue 重复返回 -1 */
    Long saveItem(SysDictItem item);

    /** 编辑字典子项 */
    void updateItem(SysDictItem item);

    /** 删除字典子项 */
    void deleteItem(Long id);

    /** 按字典编码取启用的子项列表（前端下拉框用，最常用） */
    List<SysDictItem> getEnabledItemsByCode(String typeCode);
}
