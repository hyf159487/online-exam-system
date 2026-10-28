package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.SysDictItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 字典子项 Mapper，对应 sys_dict_item 表
 */
@Mapper
public interface SysDictItemMapper {

    @Select("SELECT * FROM sys_dict_item WHERE type_id = #{typeId} ORDER BY sort_order ASC, id ASC")
    List<SysDictItem> selectByTypeId(@Param("typeId") Long typeId);

    /** 按字典编码查启用的子项（业务页面拉选项用，最常用接口） */
    @Select("SELECT i.* FROM sys_dict_item i " +
            "INNER JOIN sys_dict_type t ON t.id = i.type_id " +
            "WHERE t.type_code = #{typeCode} AND i.enabled = 1 " +
            "ORDER BY i.sort_order ASC, i.id ASC")
    List<SysDictItem> selectEnabledByTypeCode(@Param("typeCode") String typeCode);

    @Select("SELECT * FROM sys_dict_item WHERE id = #{id}")
    SysDictItem selectById(@Param("id") Long id);

    @Insert("INSERT INTO sys_dict_item (type_id, item_label, item_value, sort_order, enabled, remark) " +
            "VALUES (#{typeId}, #{itemLabel}, #{itemValue}, #{sortOrder}, #{enabled}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysDictItem item);

    @Update("UPDATE sys_dict_item SET type_id = #{typeId}, item_label = #{itemLabel}, " +
            "item_value = #{itemValue}, sort_order = #{sortOrder}, enabled = #{enabled}, " +
            "remark = #{remark} WHERE id = #{id}")
    int update(SysDictItem item);

    @Delete("DELETE FROM sys_dict_item WHERE id = #{id}")
    int delete(@Param("id") Long id);

    /** 按 type_id 删除所有子项（删除字典类型前先清空） */
    @Delete("DELETE FROM sys_dict_item WHERE type_id = #{typeId}")
    int deleteByTypeId(@Param("typeId") Long typeId);

    /** 检查同一类型下 item_value 是否重复 */
    @Select("SELECT COUNT(*) FROM sys_dict_item WHERE type_id = #{typeId} AND item_value = #{itemValue} " +
            "AND id <> #{excludeId}")
    long countDuplicateValue(@Param("typeId") Long typeId,
                             @Param("itemValue") String itemValue,
                             @Param("excludeId") Long excludeId);
}
