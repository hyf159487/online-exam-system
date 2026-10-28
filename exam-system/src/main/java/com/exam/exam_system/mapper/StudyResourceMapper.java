package com.exam.exam_system.mapper;

import com.exam.exam_system.dto.StudyResourceQuery;
import com.exam.exam_system.entity.StudyResource;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 学习资料 Mapper，对应 study_resource 表
 *
 * <p>分页 + 筛选用注解 <script> 动态 SQL；简单查询用 @Select/@Insert/@Update。
 */
@Mapper
public interface StudyResourceMapper {

    /** 分页查学习资料（联表带出课程名），传 courseId 则按课程筛 */
    @Select("<script>" +
            "SELECT r.id, r.course_id AS courseId, c.course_name AS courseName, " +
            "r.resource_name AS resourceName, r.file_type AS fileType, r.file_path AS filePath, " +
            "r.file_size AS fileSize, r.file_size_text AS fileSizeText, " +
            "r.upload_teacher_id AS uploadTeacherId, r.upload_teacher_name AS uploadTeacherName, " +
            "r.description, r.view_count AS viewCount, r.download_count AS downloadCount, " +
            "r.create_time AS createTime, r.is_deleted AS isDeleted " +
            "FROM study_resource r " +
            "LEFT JOIN sys_course c ON r.course_id = c.id " +
            "WHERE r.is_deleted = 0 " +
            "<if test='q.courseId != null'>AND r.course_id = #{q.courseId} </if>" +
            "<if test='q.courseIds != null'>AND r.course_id IN " +
            "<foreach collection='q.courseIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> </if>" +
            "<if test='q.keyword != null and q.keyword != \"\"'>AND r.resource_name LIKE CONCAT('%', #{q.keyword}, '%') </if>" +
            "ORDER BY r.create_time DESC, r.id DESC " +
            "LIMIT #{q.pageSize} OFFSET #{q.offset}" +
            "</script>")
    List<StudyResource> selectPage(@Param("q") StudyResourceQuery q);

    /** 分页计数 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM study_resource r " +
            "WHERE r.is_deleted = 0 " +
            "<if test='q.courseId != null'>AND r.course_id = #{q.courseId} </if>" +
            "<if test='q.courseIds != null'>AND r.course_id IN " +
            "<foreach collection='q.courseIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> </if>" +
            "<if test='q.keyword != null and q.keyword != \"\"'>AND r.resource_name LIKE CONCAT('%', #{q.keyword}, '%') </if>" +
            "</script>")
    long countPage(@Param("q") StudyResourceQuery q);

    /** 按 id 查（含课程名） */
    @Select("SELECT r.id, r.course_id AS courseId, c.course_name AS courseName, " +
            "r.resource_name AS resourceName, r.file_type AS fileType, r.file_path AS filePath, " +
            "r.file_size AS fileSize, r.file_size_text AS fileSizeText, " +
            "r.upload_teacher_id AS uploadTeacherId, r.upload_teacher_name AS uploadTeacherName, " +
            "r.description, r.view_count AS viewCount, r.download_count AS downloadCount, " +
            "r.create_time AS createTime, r.is_deleted AS isDeleted " +
            "FROM study_resource r " +
            "LEFT JOIN sys_course c ON r.course_id = c.id " +
            "WHERE r.id = #{id} AND r.is_deleted = 0 LIMIT 1")
    StudyResource selectById(@Param("id") Long id);

    /** 新增学习资料（文件名、类型、大小、路径、上传人等），主键回填 */
    @Insert("INSERT INTO study_resource (course_id, resource_name, file_type, file_path, " +
            "file_size, file_size_text, upload_teacher_id, upload_teacher_name, description) " +
            "VALUES (#{courseId}, #{resourceName}, #{fileType}, #{filePath}, " +
            "#{fileSize}, #{fileSizeText}, #{uploadTeacherId}, #{uploadTeacherName}, #{description})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(StudyResource r);

    /** 更新资料（名称/备注，可选换课程）；文件本体不变 */
    @Update("<script>UPDATE study_resource SET " +
            "resource_name = #{resourceName} " +
            "<if test='courseId != null'>, course_id = #{courseId} </if>" +
            "<if test='description != null'>, description = #{description} </if>" +
            " WHERE id = #{id}</script>")
    int update(StudyResource r);

    /** 逻辑删除（按 id） */
    @Update("UPDATE study_resource SET is_deleted = 1 WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /** 更新外部链接资料的链接地址（仅 file_type=link 的资料使用） */
    @Update("UPDATE study_resource SET file_path = #{filePath} WHERE id = #{id}")
    int updateFilePath(@Param("id") Long id, @Param("filePath") String filePath);

    /** 批量逻辑删除（按 id 列表） */
    @Update("<script>UPDATE study_resource SET is_deleted = 1 WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int batchDelete(@Param("ids") List<Long> ids);

    /** 学生端：按课程拉取该课程全部未删除资料（全量，按上传时间倒序） */
    @Select("SELECT r.id, r.course_id AS courseId, r.resource_name AS resourceName, " +
            "r.file_type AS fileType, r.file_path AS filePath, r.file_size AS fileSize, " +
            "r.file_size_text AS fileSizeText, r.upload_teacher_name AS uploadTeacherName, " +
            "r.description, r.view_count AS viewCount, r.download_count AS downloadCount, " +
            "r.create_time AS createTime " +
            "FROM study_resource r " +
            "WHERE r.course_id = #{courseId} AND r.is_deleted = 0 " +
            "ORDER BY r.create_time DESC, r.id DESC")
    List<StudyResource> selectByCourse(@Param("courseId") Long courseId);

    /** 学生端：按课程 ID 集合拉取全部未删除资料（已加入课程） */
    @Select("<script>SELECT r.id, r.course_id AS courseId, r.resource_name AS resourceName, " +
            "r.file_type AS fileType, r.file_path AS filePath, r.file_size AS fileSize, " +
            "r.file_size_text AS fileSizeText, r.upload_teacher_name AS uploadTeacherName, " +
            "r.description, r.view_count AS viewCount, r.download_count AS downloadCount, " +
            "r.create_time AS createTime " +
            "FROM study_resource r " +
            "WHERE r.is_deleted = 0 AND r.course_id IN " +
            "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "ORDER BY r.create_time DESC, r.id DESC</script>")
    List<StudyResource> selectByCourseIds(@Param("courseIds") List<Long> courseIds);

    /** 浏览次数 +1（在线预览时调用） */
    @Update("UPDATE study_resource SET view_count = view_count + 1 WHERE id = #{id}")
    int increaseViewCount(@Param("id") Long id);

    /** 下载次数 +1（下载资料时调用） */
    @Update("UPDATE study_resource SET download_count = download_count + 1 WHERE id = #{id}")
    int increaseDownloadCount(@Param("id") Long id);

    /** 按 id 列表查（批量删前取文件路径用） */
    @Select("<script>SELECT id, file_path AS filePath, resource_name AS resourceName FROM study_resource " +
            "WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<StudyResource> selectByIds(@Param("ids") List<Long> ids);
}
