package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.SysRole;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 角色 Mapper，对应 sys_role 表
 *
 * <p>固定 3 角色，但仍提供完整 CRUD，便于后续扩展。
 * 业务约束（service 层强制）：
 * <ul>
 *   <li>role_key='admin' 的角色禁止删除</li>
 *   <li>role_key 唯一，不能重复</li>
 * </ul>
 */
@Mapper
public interface SysRoleMapper {

    @Select("SELECT * FROM sys_role ORDER BY id ASC")
    List<SysRole> selectAll();

    @Select("SELECT * FROM sys_role WHERE id = #{id}")
    SysRole selectById(@Param("id") Long id);

    @Select("SELECT * FROM sys_role WHERE role_key = #{roleKey} LIMIT 1")
    SysRole selectByRoleKey(@Param("roleKey") String roleKey);

    @SelectProvider(type = SqlProvider.class, method = "listByQuery")
    List<SysRole> listByQuery(@Param("roleName") String roleName);

    @Insert("INSERT INTO sys_role (role_name, role_key, remark) " +
            "VALUES (#{roleName}, #{roleKey}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysRole role);

    @Update("UPDATE sys_role SET role_name = #{roleName}, remark = #{remark} " +
            "WHERE id = #{id}")
    int update(SysRole role);

    @Delete("DELETE FROM sys_role WHERE id = #{id} AND role_key <> 'admin'")
    int deleteProtected(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM sys_user WHERE role_id = #{roleId}")
    long countUsersByRoleId(@Param("roleId") Long roleId);

    /** 动态 SQL 提供器：按角色名模糊查询 */
    class SqlProvider {
        public String listByQuery(java.util.Map<String, Object> params) {
            String roleName = (String) params.get("roleName");
            StringBuilder sb = new StringBuilder("SELECT * FROM sys_role WHERE 1=1 ");
            if (roleName != null && !roleName.trim().isEmpty()) {
                sb.append(" AND role_name LIKE CONCAT('%', #{roleName}, '%') ");
            }
            sb.append(" ORDER BY id ASC ");
            return sb.toString();
        }
    }
}
