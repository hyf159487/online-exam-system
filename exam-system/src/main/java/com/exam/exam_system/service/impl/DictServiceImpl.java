package com.exam.exam_system.service.impl;

import com.exam.exam_system.entity.SysDictItem;
import com.exam.exam_system.entity.SysDictType;
import com.exam.exam_system.mapper.SysDictItemMapper;
import com.exam.exam_system.mapper.SysDictTypeMapper;
import com.exam.exam_system.service.DictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 字典服务实现
 *
 * <p>业务约束：
 * <ul>
 *   <li>is_builtin=1 的字典类型禁止删除</li>
 *   <li>字典类型下有子项时禁止删除（防止孤儿数据）</li>
 *   <li>同一类型下 item_value 不允许重复</li>
 *   <li>删除子项直接物理删除（字典子项通常数据量小）</li>
 * </ul>
 */
@Service
public class DictServiceImpl implements DictService {

    @Autowired
    private SysDictTypeMapper dictTypeMapper;

    @Autowired
    private SysDictItemMapper dictItemMapper;

    // ===== 字典类型 =====

    @Override
    public List<SysDictType> listTypes(String typeName) {
        return dictTypeMapper.listByQuery(typeName);
    }

    @Override
    public SysDictType getTypeById(Long id) {
        return dictTypeMapper.selectById(id);
    }

    @Override
    public Long saveType(SysDictType type) {
        SysDictType exists = dictTypeMapper.selectByTypeCode(type.getTypeCode());
        if (exists != null) {
            return -1L;
        }
        if (type.getIsBuiltin() == null) {
            type.setIsBuiltin(0);
        }
        dictTypeMapper.insert(type);
        return type.getId();
    }

    @Override
    public void updateType(SysDictType type) {
        SysDictType update = new SysDictType();
        update.setId(type.getId());
        update.setTypeName(type.getTypeName());
        update.setRemark(type.getRemark());
        dictTypeMapper.update(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long id) {
        SysDictType type = dictTypeMapper.selectById(id);
        if (type == null) {
            throw new IllegalArgumentException("字典类型不存在");
        }
        if (type.getIsBuiltin() != null && type.getIsBuiltin() == 1) {
            throw new IllegalStateException("系统内置字典类型禁止删除");
        }
        long itemCount = dictTypeMapper.countItemsByTypeId(id);
        if (itemCount > 0) {
            throw new IllegalStateException("该字典类型下还有 " + itemCount + " 个子项，请先删除子项");
        }
        dictTypeMapper.deleteNonBuiltin(id);
    }

    // ===== 字典子项 =====

    @Override
    public List<SysDictItem> listItemsByTypeId(Long typeId) {
        return dictItemMapper.selectByTypeId(typeId);
    }

    @Override
    public SysDictItem getItemById(Long id) {
        return dictItemMapper.selectById(id);
    }

    @Override
    public Long saveItem(SysDictItem item) {
        long dup = dictItemMapper.countDuplicateValue(item.getTypeId(), item.getItemValue(), null);
        if (dup > 0) {
            return -1L;
        }
        if (item.getSortOrder() == null) item.setSortOrder(0);
        if (item.getEnabled() == null) item.setEnabled(1);
        dictItemMapper.insert(item);
        return item.getId();
    }

    @Override
    public void updateItem(SysDictItem item) {
        long dup = dictItemMapper.countDuplicateValue(item.getTypeId(), item.getItemValue(), item.getId());
        if (dup > 0) {
            throw new IllegalStateException("同一类型下已存在相同存储值的子项");
        }
        dictItemMapper.update(item);
    }

    @Override
    public void deleteItem(Long id) {
        dictItemMapper.delete(id);
    }

    @Override
    public List<SysDictItem> getEnabledItemsByCode(String typeCode) {
        return dictItemMapper.selectEnabledByTypeCode(typeCode);
    }
}
