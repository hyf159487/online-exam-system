package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.SysDictType;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 字典类型 Mapper，对应 sys_dict_type 表
 */
@Mapper
public interface SysDictTypeMapper {

    @Select("SELECT * FROM sys_dict_type ORDER BY id ASC")
    List<SysDictType> selectAll();

    @Select("SELECT * FROM sys_dict_type WHERE id = #{id}")
    SysDictType selectById(@Param("id") Long id);

    @Select("SELECT * FROM sys_dict_type WHERE type_code = #{typeCode} LIMIT 1")
    SysDictType selectByTypeCode(@Param("typeCode") String typeCode);

    @SelectProvider(type = SqlProvider.class, method = "listByQuery")
    List<SysDictType> listByQuery(@Param("typeName") String typeName);

    @Insert("INSERT INTO sys_dict_type (type_name, type_code, remark, is_builtin) " +
            "VALUES (#{typeName}, #{typeCode}, #{remark}, #{isBuiltin})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysDictType type);

    @Update("UPDATE sys_dict_type SET type_name = #{typeName}, remark = #{remark} WHERE id = #{id}")
    int update(SysDictType type);

    /** 删除字典类型（仅非内置可删，service 层校验） */
    @Delete("DELETE FROM sys_dict_type WHERE id = #{id} AND is_builtin = 0")
    int deleteNonBuiltin(@Param("id") Long id);

    /** 统计该类型下的子项数量（删除校验用） */
    @Select("SELECT COUNT(*) FROM sys_dict_item WHERE type_id = #{typeId}")
    long countItemsByTypeId(@Param("typeId") Long typeId);

    class SqlProvider {
        public String listByQuery(java.util.Map<String, Object> params) {
            String typeName = (String) params.get("typeName");
            StringBuilder sb = new StringBuilder("SELECT * FROM sys_dict_type WHERE 1=1 ");
            if (typeName != null && !typeName.trim().isEmpty()) {
                sb.append(" AND type_name LIKE CONCAT('%', #{typeName}, '%') ");
            }
            sb.append(" ORDER BY id ASC ");
            return sb.toString();
        }
    }
}
