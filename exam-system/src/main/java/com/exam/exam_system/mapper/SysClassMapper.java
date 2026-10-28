package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.SysClass;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 班级表 Mapper，对应 sys_class
 * <p>教师端错题筛选下拉用 listAll()；管理端增删改用其余方法。
 */
@Mapper
public interface SysClassMapper {

    /** 查询全部班级（id / 班级名 / 年级 / 院系 / 专业）——教师端错题筛选下拉用 */
    @Select("SELECT id, class_name AS className, grade, department, major FROM sys_class ORDER BY id")
    List<Map<String, Object>> listAll();

    /** 查询全部班级 + 学生数 + 班主任姓名（管理端列表用） */
    @Select("SELECT c.id, c.class_name AS className, c.grade, c.department, c.major, " +
            "c.teacher_id AS teacherId, t.real_name AS teacherName, " +
            "c.create_time AS createTime, " +
            "(SELECT COUNT(*) FROM sys_user u WHERE u.class_id = c.id) AS studentCount " +
            "FROM sys_class c " +
            "LEFT JOIN sys_user t ON t.id = c.teacher_id " +
            "ORDER BY c.id")
    List<SysClass> selectAllWithCount();

    /** 按 id 查单个班级（含班主任姓名） */
    @Select("SELECT c.id, c.class_name AS className, c.grade, c.department, c.major, " +
            "c.teacher_id AS teacherId, t.real_name AS teacherName, c.create_time AS createTime " +
            "FROM sys_class c LEFT JOIN sys_user t ON t.id = c.teacher_id " +
            "WHERE c.id = #{id} LIMIT 1")
    SysClass selectById(@Param("id") Long id);

    /** 按班级名查班级（新增/更新时查重） */
    @Select("SELECT * FROM sys_class WHERE class_name = #{name} LIMIT 1")
    SysClass selectByName(@Param("name") String name);

    /** 新增班级（含专业和班主任） */
    @Insert("INSERT INTO sys_class (class_name, grade, department, major, teacher_id) " +
            "VALUES (#{className}, #{grade}, #{department}, #{major}, #{teacherId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysClass sysClass);

    /** 更新班级信息（含专业和班主任） */
    @Update("UPDATE sys_class SET class_name = #{className}, grade = #{grade}, " +
            "department = #{department}, major = #{major}, teacher_id = #{teacherId} WHERE id = #{id}")
    int update(SysClass sysClass);

    /** 删除班级 */
    @Delete("DELETE FROM sys_class WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /** 统计班级下学生数（删除校验用） */
    @Select("SELECT COUNT(*) FROM sys_user WHERE class_id = #{id}")
    long countStudents(@Param("id") Long id);
}
