package com.exam.exam_system.mapper;

import com.exam.exam_system.dto.KnowledgePointQuery;
import com.exam.exam_system.entity.KnowledgePoint;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 知识点 Mapper，对应 knowledge_point 表
 *
 * <p>分页 / 列表均按 course_id 过滤、sort_order 升序；试题引用计数用于删除校验。
 */
@Mapper
public interface KnowledgePointMapper {

    /** 分页查询（courseId 可选、keyword 匹配名称/编码；未选课程时按全部课程分页） */
    @Select("<script>" +
            "SELECT k.id, k.course_id AS courseId, c.course_name AS courseName, " +
            "k.point_name AS pointName, k.knowledge_code AS knowledgeCode, " +
            "k.parent_id AS parentId, k.sort_order AS sortOrder, k.remark, " +
            "k.create_time AS createTime " +
            "FROM knowledge_point k " +
            "LEFT JOIN sys_course c ON k.course_id = c.id " +
            "WHERE 1=1 " +
            "<if test='q.courseId != null'>AND k.course_id = #{q.courseId} </if>" +
            "<if test='q.courseIds != null'>AND k.course_id IN " +
            "<foreach collection='q.courseIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> </if>" +
            "<if test='q.keyword != null and q.keyword != \"\"'>" +
            "AND (k.point_name LIKE CONCAT('%', #{q.keyword}, '%') " +
            "     OR k.knowledge_code LIKE CONCAT('%', #{q.keyword}, '%')) </if>" +
            "ORDER BY k.sort_order ASC, k.id ASC " +
            "LIMIT #{q.pageSize} OFFSET #{q.offset}" +
            "</script>")
    List<KnowledgePoint> listByQuery(@Param("q") KnowledgePointQuery q);

    /** 分页计数 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM knowledge_point k " +
            "WHERE 1=1 " +
            "<if test='q.courseId != null'>AND k.course_id = #{q.courseId} </if>" +
            "<if test='q.courseIds != null'>AND k.course_id IN " +
            "<foreach collection='q.courseIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> </if>" +
            "<if test='q.keyword != null and q.keyword != \"\"'>" +
            "AND (k.point_name LIKE CONCAT('%', #{q.keyword}, '%') " +
            "     OR k.knowledge_code LIKE CONCAT('%', #{q.keyword}, '%')) </if>" +
            "</script>")
    long countByQuery(@Param("q") KnowledgePointQuery q);

    /** 按课程查知识点列表（无分页，供题目录入下拉 / 只读查看），按排序号升序 */
    @Select("<script>" +
            "SELECT id, course_id AS courseId, point_name AS pointName, " +
            "knowledge_code AS knowledgeCode, parent_id AS parentId, " +
            "sort_order AS sortOrder, remark, create_time AS createTime " +
            "FROM knowledge_point " +
            "WHERE 1=1 " +
            "<if test='courseId != null'>AND course_id = #{courseId} </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (point_name LIKE CONCAT('%', #{keyword}, '%') " +
            "     OR knowledge_code LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY sort_order ASC, id ASC" +
            "</script>")
    List<KnowledgePoint> listByCourse(@Param("courseId") Long courseId,
                                      @Param("keyword") String keyword);

    /** 按 id 查单个 */
    @Select("SELECT k.id, k.course_id AS courseId, c.course_name AS courseName, " +
            "k.point_name AS pointName, k.knowledge_code AS knowledgeCode, " +
            "k.parent_id AS parentId, k.sort_order AS sortOrder, k.remark, k.create_time AS createTime " +
            "FROM knowledge_point k " +
            "LEFT JOIN sys_course c ON k.course_id = c.id " +
            "WHERE k.id = #{id}")
    KnowledgePoint selectById(@Param("id") Long id);

    /** 新增知识点 */
    @Insert("INSERT INTO knowledge_point(course_id, point_name, knowledge_code, parent_id, sort_order, remark) " +
            "VALUES(#{courseId}, #{pointName}, #{knowledgeCode}, #{parentId}, #{sortOrder}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KnowledgePoint kp);

    /** 批量新增（Excel 导入用） */
    @Insert("<script>" +
            "INSERT INTO knowledge_point(course_id, point_name, knowledge_code, parent_id, sort_order, remark) VALUES " +
            "<foreach collection='list' item='k' separator=','>" +
            "(#{k.courseId}, #{k.pointName}, #{k.knowledgeCode}, #{k.parentId}, #{k.sortOrder}, #{k.remark})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<KnowledgePoint> list);

    /** 更新知识点 */
    @Update("UPDATE knowledge_point SET course_id = #{courseId}, point_name = #{pointName}, " +
            "knowledge_code = #{knowledgeCode}, parent_id = #{parentId}, " +
            "sort_order = #{sortOrder}, remark = #{remark} WHERE id = #{id}")
    int updateById(KnowledgePoint kp);

    /** 删除单个知识点 */
    @Delete("DELETE FROM knowledge_point WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /** 批量删除知识点 */
    @Delete("<script>" +
            "DELETE FROM knowledge_point WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int batchDeleteByIds(@Param("ids") List<Long> ids);

    /** 统计被试题引用的数量（删除校验：>0 禁止删除） */
    @Select("SELECT COUNT(*) FROM question WHERE knowledge_id = #{id}")
    long countQuestionRef(@Param("id") Long id);
}
