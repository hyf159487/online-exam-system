package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.SysConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 系统参数配置 Mapper，对应 sys_config 表
 *
 * <p>只提供 SELECT 和 UPDATE，不提供 INSERT/DELETE（参数由初始化脚本预置）。
 */
@Mapper
public interface SysConfigMapper {

    @Select("SELECT * FROM sys_config ORDER BY id ASC")
    List<SysConfig> selectAll();

    @Select("SELECT * FROM sys_config WHERE config_key = #{configKey} LIMIT 1")
    SysConfig selectByKey(@Param("configKey") String configKey);

    @Select("SELECT * FROM sys_config WHERE id = #{id}")
    SysConfig selectById(@Param("id") Long id);

    /** 按 ID 更新 configValue（只允许改值，其他字段不可改） */
    @Update("UPDATE sys_config SET config_value = #{configValue} WHERE id = #{id}")
    int updateValue(@Param("id") Long id, @Param("configValue") String configValue);

    /** 批量更新（服务层事务调用） */
    @Update("<script>" +
            "<foreach collection='list' item='item' separator=';'>" +
            "UPDATE sys_config SET config_value = #{item.configValue} WHERE id = #{item.id}" +
            "</foreach>" +
            "</script>")
    int batchUpdateValues(@Param("list") List<SysConfig> list);
}
