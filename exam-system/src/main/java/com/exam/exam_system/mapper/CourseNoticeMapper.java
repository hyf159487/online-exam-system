package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.CourseNotice;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 课程公告 Mapper，对应 course_notice 表
 * <p>公告按课程维度组织：教师端发布/修改/删除，学生端只读（仅已加入课程可看）。
 */
@Mapper
public interface CourseNoticeMapper {

    /** 发布课程公告 */
    @Insert("INSERT INTO course_notice (course_id, title, content, teacher_id, teacher_name) " +
            "VALUES (#{courseId}, #{title}, #{content}, #{teacherId}, #{teacherName})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CourseNotice notice);

    /** 修改公告标题与正文（仅未删除的公告） */
    @Update("UPDATE course_notice SET title = #{title}, content = #{content}, update_time = NOW() " +
            "WHERE id = #{id} AND is_deleted = 0")
    int update(CourseNotice notice);

    /** 逻辑删除公告 */
    @Update("UPDATE course_notice SET is_deleted = 1, update_time = NOW() WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /** 按 id 查公告（未删除） */
    @Select("SELECT * FROM course_notice WHERE id = #{id} AND is_deleted = 0 LIMIT 1")
    CourseNotice selectById(@Param("id") Long id);

    /** 某课程的公告列表（按发布时间倒序，联查课程名称） */
    @Select("SELECT n.id, n.course_id AS courseId, n.title, n.content, " +
            "n.teacher_id AS teacherId, n.teacher_name AS teacherName, " +
            "n.create_time AS createTime, n.update_time AS updateTime, " +
            "c.course_name AS courseName " +
            "FROM course_notice n LEFT JOIN sys_course c ON c.id = n.course_id " +
            "WHERE n.course_id = #{courseId} AND n.is_deleted = 0 " +
            "ORDER BY n.create_time DESC, n.id DESC")
    List<CourseNotice> listByCourse(@Param("courseId") Long courseId);

    /** 某课程的公告数量（教师端课程卡片展示用） */
    @Select("SELECT COUNT(*) FROM course_notice WHERE course_id = #{courseId} AND is_deleted = 0")
    long countByCourse(@Param("courseId") Long courseId);
}
