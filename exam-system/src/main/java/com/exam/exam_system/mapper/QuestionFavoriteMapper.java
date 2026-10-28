package com.exam.exam_system.mapper;

import com.exam.exam_system.dto.FavoriteQuery;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.entity.QuestionFavorite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 试题收藏 Mapper，对应 question_favorite 表。
 *
 * <p>表只存 user_id / question_id / create_time，并带 uk_user_question(user_id, question_id)
 * 唯一约束做 DB 级去重；题干、选项、答案、难度等通过 LEFT JOIN question / sys_course 回显。
 *
 * <p>所有查询都以 user_id 为第一条件，保证学生之间互不可见、不可操作。
 */
@Mapper
public interface QuestionFavoriteMapper {

    /**
     * 学生端收藏筛选条件片段（列表 / 总数 / 导出三处复用，避免 SQL 不一致）。
     * 注意：keyword 的空白串已由 Service 归一为 null，这里只判空即可。
     */
    String STUDENT_FILTER =
            "<if test='q.courseId != null'> AND q.course_id = #{q.courseId} </if>" +
            "<if test='q.questionType != null'> AND q.question_type = #{q.questionType} </if>" +
            "<if test='q.keyword != null'> AND q.content LIKE CONCAT('%', #{q.keyword}, '%') </if>";

    /** 学生端收藏查询公共字段（联 question 带出题干/选项/答案/解析/难度，联 sys_course 带出学科名） */
    String STUDENT_COLUMNS =
            "SELECT f.id AS id, f.user_id AS user_id, f.question_id AS question_id, " +
            "f.create_time AS create_time, " +
            "q.content AS content, q.options AS options, q.answer AS answer, " +
            "q.analysis AS analysis, q.difficulty AS difficulty, " +
            "q.question_type AS question_type, q.score AS score, q.course_id AS course_id, " +
            "c.course_name AS course_name " +
            "FROM question_favorite f " +
            "LEFT JOIN question q ON f.question_id = q.id " +
            "LEFT JOIN sys_course c ON q.course_id = c.id ";

    /**
     * 收藏一道题（幂等）：命中 uk_user_question 唯一键时走 ON DUPLICATE KEY UPDATE 空更新，
     * 既不会抛唯一键异常，也不会吞掉其它插入错误。
     * @return 1=本次新收藏；0=之前已收藏（影响行数为 0）
     */
    @Insert("INSERT INTO question_favorite(user_id, question_id) VALUES (#{userId}, #{questionId}) " +
            "ON DUPLICATE KEY UPDATE id = id")
    int insertIfAbsent(@Param("userId") Long userId, @Param("questionId") Long questionId);

    /** 该学生是否已收藏某题（1=已收藏） */
    @Select("SELECT COUNT(*) FROM question_favorite WHERE user_id = #{userId} AND question_id = #{questionId}")
    int countByUserAndQuestion(@Param("userId") Long userId, @Param("questionId") Long questionId);

    /**
     * 当前学生已收藏的全部试题 ID（练习页加载一轮题后用来标记「已收藏」星标）。
     * 收藏量级很小（学生手动标记），一次性返回比逐题查询更省往返。
     */
    @Select("SELECT question_id FROM question_favorite WHERE user_id = #{userId}")
    List<Long> listQuestionIds(@Param("userId") Long userId);

    /** 我的收藏：分页列表，支持学科/题型/题干关键词筛选，按收藏时间倒序 */
    @Select("<script>" + STUDENT_COLUMNS +
            "WHERE f.user_id = #{userId} " + STUDENT_FILTER +
            "ORDER BY f.create_time DESC, f.id DESC LIMIT #{q.pageSize} OFFSET #{q.offset}" +
            "</script>")
    List<QuestionFavorite> listByUser(@Param("userId") Long userId, @Param("q") FavoriteQuery q);

    /** 我的收藏：总数（与列表共用同一套筛选条件） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM question_favorite f " +
            "LEFT JOIN question q ON f.question_id = q.id " +
            "WHERE f.user_id = #{userId} " + STUDENT_FILTER +
            "</script>")
    long countByUser(@Param("userId") Long userId, @Param("q") FavoriteQuery q);

    /** 我的收藏：按筛选条件取全部（导出 Excel 用，不分页，SQL 侧限流 5000 条） */
    @Select("<script>" + STUDENT_COLUMNS +
            "WHERE f.user_id = #{userId} " + STUDENT_FILTER +
            "ORDER BY f.create_time DESC, f.id DESC LIMIT 5000" +
            "</script>")
    List<QuestionFavorite> listForExport(@Param("userId") Long userId, @Param("q") FavoriteQuery q);

    /** 取消收藏（按收藏记录 ID，只删自己的） */
    @Delete("DELETE FROM question_favorite WHERE id = #{id} AND user_id = #{userId}")
    int deleteByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    /** 取消收藏（按试题 ID，练习页「取消收藏」按钮用，只删自己的） */
    @Delete("DELETE FROM question_favorite WHERE user_id = #{userId} AND question_id = #{questionId}")
    int deleteByUserAndQuestion(@Param("userId") Long userId, @Param("questionId") Long questionId);

    /** 批量取消收藏（只删自己的，IN 条件由 SQL 保证不会越权） */
    @Delete("<script>" +
            "DELETE FROM question_favorite WHERE user_id = #{userId} AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int deleteByIds(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    /**
     * 收藏题练习抽题：从当前学生收藏里随机抽取 N 道题（联 question 带出题干/选项/答案/解析）。
     *
     * <p>「我的收藏」页的批量练习入口用；courseId / questionTypes 可选，不传表示不限制。
     * 表上 uk_user_question 保证同一学生同一题只有一条，无需 DISTINCT。</p>
     */
    @Select("<script>" +
            "SELECT q.id AS id, q.course_id AS courseId, q.question_type AS questionType, " +
            "q.content AS content, q.options AS options, q.answer AS answer, " +
            "q.analysis AS analysis, q.score AS score, q.difficulty AS difficulty " +
            "FROM question_favorite f " +
            "JOIN question q ON q.id = f.question_id " +
            "WHERE f.user_id = #{studentId} " +
            "<if test='courseId != null'> AND q.course_id = #{courseId} </if>" +
            "<if test='questionTypes != null and questionTypes.size() > 0'>" +
            "AND q.question_type IN " +
            "<foreach collection='questionTypes' item='t' open='(' separator=',' close=')'>#{t}</foreach> " +
            "</if>" +
            "ORDER BY RAND() LIMIT #{count}" +
            "</script>")
    List<Question> randomPickFromFavorite(@Param("studentId") Long studentId,
                                          @Param("courseId") Long courseId,
                                          @Param("questionTypes") List<Integer> questionTypes,
                                          @Param("count") Integer count);

    /** 我的收藏：总览统计（收藏总题数） */
    @Select("SELECT COUNT(*) AS total FROM question_favorite WHERE user_id = #{userId}")
    Map<String, Object> overviewByUser(@Param("userId") Long userId);

    /** 我的收藏：按学科统计收藏数量（统计卡片里的学科分布标签用） */
    @Select("SELECT q.course_id AS courseId, c.course_name AS courseName, COUNT(*) AS count " +
            "FROM question_favorite f " +
            "LEFT JOIN question q ON f.question_id = q.id " +
            "LEFT JOIN sys_course c ON q.course_id = c.id " +
            "WHERE f.user_id = #{userId} " +
            "GROUP BY q.course_id, c.course_name ORDER BY count DESC")
    List<Map<String, Object>> statsByCourse(@Param("userId") Long userId);
}
