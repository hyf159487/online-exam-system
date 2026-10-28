package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.SysUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;

import java.util.List;
import java.util.Map;

/**
 * 用户表 Mapper，对应 sys_user
 * 用注解写简单 SQL；复杂 SQL（多表关联、动态条件）用 @SelectProvider
 */
@Mapper
public interface SysUserMapper {

    /** 通过用户名查单个用户（登录用） */
    @Select("SELECT * FROM sys_user WHERE username = #{username} LIMIT 1")
    SysUser selectByUsername(String username);

    /** 按 id 查单个用户（读取错题自动移除策略用） */
    @Select("SELECT * FROM sys_user WHERE id = #{id} LIMIT 1")
    SysUser selectById(@Param("id") Long id);

    /**
     * 查询用户个人信息（管理员/教师/学生通用，联表 sys_class 取班级名称/年级/院系）。
     * 仅返回对外展示字段，不取密码、openid 等敏感列。
     * 字段别名与接口返回 JSON 直接对齐：
     *   id, username, realName, role, roleName（"管理员/教师/学生"），
     *   className/grade/department（学生专属，管理员/教师为 null），
     *   phone/email/avatar（头像可访问路径，未设置时为 null）/createTime（账号创建时间，只读展示）/status。
     */
    @Select("SELECT u.id             AS id, " +
            "       u.username       AS username, " +
            "       u.real_name      AS realName, " +
            "       u.role           AS role, " +
            "       CASE u.role WHEN 2 THEN '管理员' WHEN 1 THEN '教师' ELSE '学生' END AS roleName, " +
            "       c.class_name     AS className, " +
            "       c.grade          AS grade, " +
            "       c.department     AS department, " +
            "       u.phone          AS phone, " +
            "       u.email          AS email, " +
            "       u.avatar         AS avatar, " +
            "       u.create_time    AS createTime, " +
            "       u.status         AS status " +
            "  FROM sys_user u " +
            "  LEFT JOIN sys_class c ON u.class_id = c.id " +
            " WHERE u.id = #{id} LIMIT 1")
    Map<String, Object> selectProfileById(@Param("id") Long id);

    /**
     * 仅更新当前账号的手机号与邮箱（学生端个人信息维护）。
     * SQL 层面就限定只动这两列，即便前端传来学号/姓名等字段也无法篡改。
     */
    @Update("UPDATE sys_user SET phone = #{phone}, email = #{email}, update_time = NOW() WHERE id = #{id}")
    int updateContact(@Param("id") Long id, @Param("phone") String phone, @Param("email") String email);

    /**
     * 仅更新当前账号头像路径（个人信息页头像上传用）。
     * 同样在 SQL 层限定只动 avatar 一列，身份字段无法被顺带篡改。
     */
    @Update("UPDATE sys_user SET avatar = #{avatar}, update_time = NOW() WHERE id = #{id}")
    int updateAvatar(@Param("id") Long id, @Param("avatar") String avatar);

    /** 更新错题自动移除策略（0/1 开关 + 1/3 阈值） */
    @Update("UPDATE sys_user SET wrong_auto_remove = #{auto}, wrong_remove_threshold = #{threshold} WHERE id = #{id}")
    int updatePracticeConfig(@Param("id") Long id, @Param("auto") Integer auto, @Param("threshold") Integer threshold);

    /** 按角色查全部（1=学生 2=教师/管理员） */
    @Select("SELECT * FROM sys_user WHERE role = #{role} AND status = 1 ORDER BY id DESC")
    List<SysUser> listByRole(Integer role);

    /**
     * 按角色查用户数（含 status=1 条件，统计活跃账号）
     * role=null 时统计全部
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM sys_user WHERE status = 1 " +
            "<if test='role != null'> AND role = #{role}</if>" +
            "</script>")
    long countByRole(@Param("role") Integer role);

    /**
     * 更新用户状态（启用/禁用）
     * @param id 用户ID
     * @param status 0=禁用 1=启用
     */
    @Update("UPDATE sys_user SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 更新密码（重置密码用，密码已在前端 BCrypt 哈希后传入）
     */
    @Update("UPDATE sys_user SET password = #{password} WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    /**
     * 调整账号所属班级（管理员调班用）
     * classId 传 null 表示取消班级归属
     */
    @Update("UPDATE sys_user SET class_id = #{classId} WHERE id = #{id}")
    int updateClassId(@Param("id") Long id, @Param("classId") Long classId);

    /**
     * 按班级查学生列表（班级管理 - 查看学生用）
     */
    @Select("SELECT * FROM sys_user WHERE role = 0 AND class_id = #{classId} ORDER BY username ASC")
    List<SysUser> selectByClassId(@Param("classId") Long classId);

    /**
     * 批量调班（管理员，单人/批量复用同一接口）
     * SQL 层兜底校验：仅学生（role=0）可调；已在目标班级的自动跳过（不能调到当前班级）；禁用账号允许调班
     * @return 实际迁移的学生数
     */
    @Update("<script>" +
            "UPDATE sys_user SET class_id = #{classId}, update_time = NOW() " +
            "WHERE role = 0 AND (class_id IS NULL OR class_id != #{classId}) " +
            "AND id IN " +
            "<foreach collection='ids' item='item' open='(' separator=',' close=')'>#{item}</foreach>" +
            "</script>")
    int updateClassBatch(@Param("ids") List<Long> ids, @Param("classId") Long classId);

    /**
     * 批量移出本班（把学生 class_id 置 null，账号保留）
     * SQL 层兜底：仅学生（role=0）、当前有班级的才需要更新
     * @return 实际移出的学生数
     */
    @Update("<script>" +
            "UPDATE sys_user SET class_id = NULL, update_time = NOW() " +
            "WHERE role = 0 AND class_id IS NOT NULL " +
            "AND id IN " +
            "<foreach collection='ids' item='item' open='(' separator=',' close=')'>#{item}</foreach>" +
            "</script>")
    int clearClassBatch(@Param("ids") List<Long> ids);

    /**
     * 分页查用户 + 模糊搜索 + 角色筛选。
     * 由内部 SqlProvider 拼条件，pageSize 上限 100。
     */
    @SelectProvider(type = SqlProvider.class, method = "listUser")
    List<SysUser> listByQuery(@Param("q") com.exam.exam_system.dto.UserQuery q);

    /** 配合 listByQuery 的总数查询 */
    @SelectProvider(type = SqlProvider.class, method = "countUser")
    long countByQuery(@Param("q") com.exam.exam_system.dto.UserQuery q);

    /**
     * 新增用户（账号管理 - 管理员新增账号用）
     * 不插 id（自增）、openid/avatar（默认 NULL）、create_time/update_time（DB 默认值）
     */
    @Insert("INSERT INTO sys_user (username, password, real_name, role, class_id, phone, status) " +
            "VALUES (#{username}, #{password}, #{realName}, #{role}, #{classId}, #{phone}, #{status})")
    int insert(SysUser user);

    /**
     * 批量删除账号（管理员用）
     * SQL 层直接排除 admin 账号，防止误删超级管理员；返回实际删除条数
     */
    @Delete("<script>" +
            "DELETE FROM sys_user WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            " AND username != 'admin'" +
            "</script>")
    int deleteByIds(@Param("ids") java.util.List<Long> ids);

    /**
     * 按 id 批量查询用户（删除前校验角色用：判断待删除账号里有哪些是管理员）
     */
    @Select("<script>SELECT * FROM sys_user WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    java.util.List<com.exam.exam_system.entity.SysUser> selectByIds(@Param("ids") java.util.List<Long> ids);

    /**
     * 查询给定账号名中已存在的（批量新增前的前端校验用）
     * @return 已存在的 username 列表
     */
    @Select("<script>" +
            "SELECT username FROM sys_user WHERE username IN " +
            "<foreach collection='usernames' item='u' open='(' separator=',' close=')'>#{u}</foreach>" +
            "</script>")
    java.util.List<String> selectExistingUsernames(@Param("usernames") java.util.List<String> usernames);

    /** 动态 SQL 提供器 */
    class SqlProvider {
        public String listUser(Map<String, Object> params) {
            com.exam.exam_system.dto.UserQuery q =
                    (com.exam.exam_system.dto.UserQuery) params.get("q");
            StringBuilder sb = new StringBuilder("SELECT * FROM sys_user WHERE 1=1 ");
            if (q.getRole() != null) {
                sb.append(" AND role = #{q.role} ");
            }
            if (q.getClassId() != null) {
                sb.append(" AND class_id = #{q.classId} ");
            }
            if (q.getUsername() != null && !q.getUsername().isEmpty()) {
                sb.append(" AND username = #{q.username} ");
            }
            if (q.getRealName() != null && !q.getRealName().isEmpty()) {
                sb.append(" AND real_name LIKE CONCAT('%', #{q.realName}, '%') ");
            }
            sb.append(" ORDER BY CAST(username AS UNSIGNED) ASC, id ASC LIMIT #{q.offset}, #{q.pageSize}");
            return sb.toString();
        }

        public String countUser(Map<String, Object> params) {
            com.exam.exam_system.dto.UserQuery q =
                    (com.exam.exam_system.dto.UserQuery) params.get("q");
            StringBuilder sb = new StringBuilder("SELECT COUNT(*) FROM sys_user WHERE 1=1 ");
            if (q.getRole() != null) {
                sb.append(" AND role = #{q.role} ");
            }
            if (q.getClassId() != null) {
                sb.append(" AND class_id = #{q.classId} ");
            }
            if (q.getUsername() != null && !q.getUsername().isEmpty()) {
                sb.append(" AND username = #{q.username} ");
            }
            if (q.getRealName() != null && !q.getRealName().isEmpty()) {
                sb.append(" AND real_name LIKE CONCAT('%', #{q.realName}, '%') ");
            }
            return sb.toString();
        }
    }
}