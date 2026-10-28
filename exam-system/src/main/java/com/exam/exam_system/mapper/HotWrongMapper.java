package com.exam.exam_system.mapper;

import com.exam.exam_system.dto.HotWrongItem;
import com.exam.exam_system.dto.HotWrongQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 高频错题 Mapper
 *
 * <p>基于 exam_answer 答题记录表实时聚合统计每道题的错误率。
 * 仅统计已判分的作答（is_correct 不为 NULL），即客观题（单选/多选/判断/填空）。
 */
@Mapper
public interface HotWrongMapper {

    /**
     * 高频错题列表查询
     */
    @Select("<script>" +
            "SELECT q.id AS id, q.content AS content, q.question_type AS questionType, " +
            "q.difficulty, q.options, q.answer, q.analysis, " +
            "q.course_id AS courseId, c.course_name AS courseName, " +
            "COUNT(ea.id) AS totalCount, " +
            "SUM(CASE WHEN ea.is_correct = 0 THEN 1 ELSE 0 END) AS wrongCount, " +
            "ROUND(SUM(CASE WHEN ea.is_correct = 0 THEN 1 ELSE 0 END) * 100.0 / COUNT(ea.id), 2) AS errorRate " +
            "FROM exam_answer ea " +
            "INNER JOIN question q ON ea.question_id = q.id " +
            "LEFT JOIN sys_course c ON q.course_id = c.id " +
            "WHERE ea.is_correct IS NOT NULL " +
            "<if test='questionType != null'>AND q.question_type = #{questionType} </if>" +
            "<if test='difficulty != null'>AND q.difficulty = #{difficulty} </if>" +
            "<if test='courseId != null'>AND q.course_id = #{courseId} </if>" +
            "<if test='courseIds != null'>AND q.course_id IN " +
            "<foreach collection='courseIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> </if>" +
            "GROUP BY q.id, q.content, q.question_type, q.difficulty, q.options, q.answer, q.analysis, q.course_id, c.course_name " +
            "<if test='minErrorRate != null'>HAVING errorRate &gt;= #{minErrorRate} </if>" +
            "<choose>" +
            "  <when test='sortBy == null or sortBy == \"errorRate_desc\"'>ORDER BY errorRate DESC</when>" +
            "  <when test='sortBy == \"totalCount_desc\"'>ORDER BY totalCount DESC</when>" +
            "  <when test='sortBy == \"wrongCount_desc\"'>ORDER BY wrongCount DESC</when>" +
            "  <otherwise>ORDER BY errorRate DESC</otherwise>" +
            "</choose> " +
            "LIMIT #{offset}, #{pageSize}" +
            "</script>")
    List<HotWrongItem> list(@Param("questionType") Integer questionType,
                            @Param("difficulty") Integer difficulty,
                            @Param("courseId") Long courseId,
                            @Param("courseIds") List<Long> courseIds,
                            @Param("minErrorRate") Double minErrorRate,
                            @Param("sortBy") String sortBy,
                            @Param("offset") int offset,
                            @Param("pageSize") int pageSize);

    /**
     * 高频错题计数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM (" +
            "  SELECT q.id FROM exam_answer ea " +
            "  INNER JOIN question q ON ea.question_id = q.id " +
            "  WHERE ea.is_correct IS NOT NULL " +
            "  <if test='questionType != null'>AND q.question_type = #{questionType} </if>" +
            "  <if test='difficulty != null'>AND q.difficulty = #{difficulty} </if>" +
            "  <if test='courseId != null'>AND q.course_id = #{courseId} </if>" +
            "  GROUP BY q.id " +
            "  <if test='minErrorRate != null'>" +
            "    HAVING ROUND(SUM(CASE WHEN ea.is_correct = 0 THEN 1 ELSE 0 END) * 100.0 / COUNT(ea.id), 2) &gt;= #{minErrorRate} " +
            "  </if>" +
            ") t" +
            "</script>")
    long count(@Param("questionType") Integer questionType,
               @Param("difficulty") Integer difficulty,
               @Param("courseId") Long courseId,
               @Param("courseIds") List<Long> courseIds,
               @Param("minErrorRate") Double minErrorRate);

    /**
     * 单题错误率统计（用于详情弹窗）
     */
    @Select("SELECT " +
            "COUNT(*) AS totalCount, " +
            "SUM(CASE WHEN is_correct = 0 THEN 1 ELSE 0 END) AS wrongCount, " +
            "ROUND(SUM(CASE WHEN is_correct = 0 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) AS errorRate " +
            "FROM exam_answer " +
            "WHERE question_id = #{questionId} AND is_correct IS NOT NULL")
    HotWrongItem statByQuestionId(@Param("questionId") Long questionId);
}
