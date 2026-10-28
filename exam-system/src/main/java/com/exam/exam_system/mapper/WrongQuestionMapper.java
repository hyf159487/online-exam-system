package com.exam.exam_system.mapper;

import com.exam.exam_system.dto.TeacherWrongQuery;
import com.exam.exam_system.dto.WrongQuery;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.entity.WrongQuestion;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 错题本 Mapper，对应 wrong_question 表。
 *
 * <p>表结构（含迁移脚本追加的列）：
 * id / user_id / question_id / exam_id / paper_id / course_id / question_type /
 * user_answer / correct_answer / score / record_id / wrong_count / correct_count /
 * last_wrong_time / last_answer_time，并带 uk_user_question(user_id, question_id) 唯一约束做 DB 级去重。
 *
 * <p>correct_count 语义为「连续答对次数」：做对 +1、答错清 0（见 updateAnswer / incrementCorrectCount），
 * 达到学生策略阈值后由 Service 自动移出错题本。
 */
public interface WrongQuestionMapper {

    /**
     * 学生端错题筛选条件片段（列表 / 总数 / 导出三处复用，避免 SQL 不一致）。
     * 注意：整体嵌在 script 标签里，比较符要写成 XML 转义形式（&amp;gt;= / &amp;lt;=）才能被正确解析。
     */
    String STUDENT_FILTER =
            "<if test='q.courseId != null'> AND w.course_id = #{q.courseId} </if>" +
            "<if test='q.questionType != null'> AND w.question_type = #{q.questionType} </if>" +
            "<if test='q.wrongCountMin != null'> AND w.wrong_count &gt;= #{q.wrongCountMin} </if>" +
            "<if test='q.wrongCountMax != null'> AND w.wrong_count &lt;= #{q.wrongCountMax} </if>";

    /** 学生端错题查询公共字段（联 question 带出题干/选项/解析，联 sys_course 带出学科名） */
    String STUDENT_COLUMNS =
            "SELECT w.*, q.content AS content, q.options AS options, q.analysis AS analysis, " +
            "c.course_name AS course_name " +
            "FROM wrong_question w " +
            "LEFT JOIN question q ON w.question_id = q.id " +
            "LEFT JOIN sys_course c ON w.course_id = c.id ";

    /** 插入一条错题（交卷时由后端自动归集）；wrong_count/correct_count/last_answer_time 由列默认值填充 */
    @Insert("INSERT INTO wrong_question(user_id, exam_id, paper_id, question_id, course_id, " +
            "question_type, user_answer, correct_answer, score, record_id) " +
            "VALUES(#{userId}, #{examId}, #{paperId}, #{questionId}, #{courseId}, " +
            "#{questionType}, #{userAnswer}, #{correctAnswer}, #{score}, #{recordId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WrongQuestion w);

    /** 同一学生对同一道题查是否已收录（去重：已存在则刷新，不重复插入） */
    @Select("SELECT * FROM wrong_question WHERE user_id = #{userId} AND question_id = #{questionId} LIMIT 1")
    WrongQuestion findByUserAndQuestion(@Param("userId") Long userId, @Param("questionId") Long questionId);

    /**
     * 答错：刷新答案/题型/学科/来源，错误次数 +1、刷新最近错误时间，
     * 并把「连续答对次数」清零（连续答对的定义：中途答错即重新计数）。
     */
    @Update("UPDATE wrong_question SET user_answer = #{userAnswer}, correct_answer = #{correctAnswer}, " +
            "score = #{score}, question_type = #{questionType}, course_id = #{courseId}, " +
            "record_id = #{recordId}, wrong_count = wrong_count + 1, correct_count = 0, " +
            "last_wrong_time = NOW(), last_answer_time = NOW() " +
            "WHERE id = #{id}")
    int updateAnswer(WrongQuestion w);

    /** 学生错题本：分页列表，支持学科/题型/错误次数区间筛选 */
    @Select("<script>" + STUDENT_COLUMNS +
            "WHERE w.user_id = #{userId} " + STUDENT_FILTER +
            "ORDER BY w.last_wrong_time DESC LIMIT #{q.pageSize} OFFSET #{q.offset}" +
            "</script>")
    List<WrongQuestion> listByUser(@Param("userId") Long userId, @Param("q") WrongQuery q);

    /** 学生错题本：总数（与列表共用同一套筛选条件） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM wrong_question w WHERE w.user_id = #{userId} " + STUDENT_FILTER +
            "</script>")
    long countByUser(@Param("userId") Long userId, @Param("q") WrongQuery q);

    /** 学生错题本：按筛选条件取全部错题（导出 Excel 用，不分页，SQL 侧限流 5000 条） */
    @Select("<script>" + STUDENT_COLUMNS +
            "WHERE w.user_id = #{userId} " + STUDENT_FILTER +
            "ORDER BY w.last_wrong_time DESC LIMIT 5000" +
            "</script>")
    List<WrongQuestion> listForExport(@Param("userId") Long userId, @Param("q") WrongQuery q);

    /** 学生错题本：总览统计（错题总数 / 已做对过题数 / 累计答错次数） */
    @Select("SELECT COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN correct_count > 0 THEN 1 ELSE 0 END), 0) AS mastered, " +
            "COALESCE(SUM(wrong_count), 0) AS wrongTotal " +
            "FROM wrong_question WHERE user_id = #{userId}")
    Map<String, Object> overviewByUser(@Param("userId") Long userId);

    /** 移除一条错题（重做正确后 / 手动移除调用） */
    @Delete("DELETE FROM wrong_question WHERE id = #{id} AND user_id = #{userId}")
    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    /** 批量移除错题（只删自己的，IN 条件由 SQL 保证不会越权） */
    @Delete("<script>" +
            "DELETE FROM wrong_question WHERE user_id = #{userId} AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int deleteByIds(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    /** 重做答对一次：correct_count +1（用于「连续答对自动移除」策略判定） */
    @Update("UPDATE wrong_question SET correct_count = correct_count + 1, last_answer_time = NOW() " +
            "WHERE id = #{id} AND user_id = #{userId}")
    int incrementCorrectCount(@Param("id") Long id, @Param("userId") Long userId);

    /** 按 id + 用户查单条错题（重做前加载，校验归属） */
    @Select("SELECT * FROM wrong_question WHERE id = #{id} AND user_id = #{userId} LIMIT 1")
    WrongQuestion findByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    /** 按 id + 用户查单条错题详情：联 question 带出题干/选项/解析，联 sys_course 带出学科名（AI 讲解用） */
    @Select("SELECT w.*, q.content AS content, q.options AS options, q.analysis AS analysis, " +
            "c.course_name AS course_name " +
            "FROM wrong_question w " +
            "LEFT JOIN question q ON w.question_id = q.id " +
            "LEFT JOIN sys_course c ON w.course_id = c.id " +
            "WHERE w.id = #{id} AND w.user_id = #{userId} LIMIT 1")
    WrongQuestion findDetailByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 错题强化练习抽题：从当前学生错题本随机抽取 N 道题（联 question 带出题干/选项/答案/解析）。
     *
     * <p>「我的错题」页的批量练习入口用；courseId / questionTypes / 错误次数区间均可选，
     * 不传表示不限制。表上 uk_user_question 保证同一学生同一题只有一条，无需 DISTINCT。</p>
     *
     * <p>注意：比较符要写成 XML 转义形式（&amp;gt;= / &amp;lt;=）才能被 script 标签正确解析。</p>
     */
    @Select("<script>" +
            "SELECT q.id AS id, q.course_id AS courseId, q.question_type AS questionType, " +
            "q.content AS content, q.options AS options, q.answer AS answer, " +
            "q.analysis AS analysis, q.score AS score, q.difficulty AS difficulty " +
            "FROM wrong_question w " +
            "JOIN question q ON q.id = w.question_id " +
            "WHERE w.user_id = #{studentId} " +
            "<if test='courseId != null'> AND w.course_id = #{courseId} </if>" +
            "<if test='questionTypes != null and questionTypes.size() > 0'>" +
            "AND w.question_type IN " +
            "<foreach collection='questionTypes' item='t' open='(' separator=',' close=')'>#{t}</foreach> " +
            "</if>" +
            "<if test='wrongCountMin != null'> AND w.wrong_count &gt;= #{wrongCountMin} </if>" +
            "<if test='wrongCountMax != null'> AND w.wrong_count &lt;= #{wrongCountMax} </if>" +
            "ORDER BY RAND() LIMIT #{count}" +
            "</script>")
    List<Question> randomPickFromWrong(@Param("studentId") Long studentId,
                                       @Param("courseId") Long courseId,
                                       @Param("questionTypes") List<Integer> questionTypes,
                                       @Param("wrongCountMin") Integer wrongCountMin,
                                       @Param("wrongCountMax") Integer wrongCountMax,
                                       @Param("count") Integer count);

    /** 某学生某学科已收录进错题本的题目数（自由刷题页的学科画像用） */
    @Select("SELECT COUNT(*) FROM wrong_question WHERE user_id = #{userId} AND course_id = #{courseId}")
    long countByUserAndCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /** 按学科统计错题数量（错题本首页分组展示用） */
    @Select("SELECT w.course_id AS courseId, c.course_name AS courseName, COUNT(*) AS count " +
            "FROM wrong_question w LEFT JOIN sys_course c ON w.course_id = c.id " +
            "WHERE w.user_id = #{userId} GROUP BY w.course_id, c.course_name")
    List<Map<String, Object>> statsByCourse(@Param("userId") Long userId);

    // ===================== 教师端：全班错题查询 =====================

    /** 教师端：全班错题列表，联学生/班级/题目/学科，支持多维筛选。
     *  注意：返回 Map 时 MyBatis 不会自动把下划线转驼峰，所以这里显式用驼峰别名。 */
    @Select("<script>" +
            "SELECT w.id AS id, w.user_id AS userId, w.question_id AS questionId, " +
            "w.exam_id AS examId, w.paper_id AS paperId, w.course_id AS courseId, " +
            "w.question_type AS questionType, w.user_answer AS userAnswer, " +
            "w.correct_answer AS correctAnswer, w.score AS score, " +
            "w.record_id AS recordId, w.wrong_count AS wrongCount, " +
            "w.last_wrong_time AS lastWrongTime, " +
            "u.real_name AS studentName, u.username AS studentNo, " +
            "c.class_name AS className, q.content AS content, q.options AS options, " +
            "sc.course_name AS courseName " +
            "FROM wrong_question w " +
            "JOIN sys_user u ON w.user_id = u.id " +
            "LEFT JOIN sys_class c ON u.class_id = c.id " +
            "LEFT JOIN question q ON w.question_id = q.id " +
            "LEFT JOIN sys_course sc ON w.course_id = sc.id " +
            "WHERE 1=1 " +
            "<if test='q.studentId != null'> AND w.user_id = #{q.studentId} </if>" +
            "<if test='q.classId != null'> AND u.class_id = #{q.classId} </if>" +
            "<if test='q.courseId != null'> AND w.course_id = #{q.courseId} </if>" +
            "<if test='q.courseIds != null'> AND w.course_id IN " +
            "<foreach collection='q.courseIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> </if>" +
            "<if test='q.questionType != null'> AND w.question_type = #{q.questionType} </if>" +
            "ORDER BY w.wrong_count DESC, w.last_wrong_time DESC " +
            "LIMIT #{q.pageSize} OFFSET #{q.offset}" +
            "</script>")
    List<Map<String, Object>> listByTeacher(@Param("q") TeacherWrongQuery q);

    /** 教师端：全班错题总数（配合上面分页） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM wrong_question w JOIN sys_user u ON w.user_id = u.id " +
            "WHERE 1=1 " +
            "<if test='q.studentId != null'> AND w.user_id = #{q.studentId} </if>" +
            "<if test='q.classId != null'> AND u.class_id = #{q.classId} </if>" +
            "<if test='q.courseId != null'> AND w.course_id = #{q.courseId} </if>" +
            "<if test='q.courseIds != null'> AND w.course_id IN " +
            "<foreach collection='q.courseIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> </if>" +
            "<if test='q.questionType != null'> AND w.question_type = #{q.questionType} </if>" +
            "</script>")
    long countByTeacher(@Param("q") TeacherWrongQuery q);

    /** 教师端：按学科统计错题数 + 涉及学生数 */
    @Select("<script>" +
            "SELECT w.course_id AS courseId, sc.course_name AS courseName, COUNT(*) AS wrongCount, " +
            "COUNT(DISTINCT w.user_id) AS studentCount " +
            "FROM wrong_question w LEFT JOIN sys_course sc ON w.course_id = sc.id " +
            "JOIN sys_user u ON w.user_id = u.id WHERE 1=1 " +
            "<if test='q.classId != null'> AND u.class_id = #{q.classId} </if>" +
            "<if test='q.courseId != null'> AND w.course_id = #{q.courseId} </if>" +
            "<if test='q.courseIds != null'> AND w.course_id IN " +
            "<foreach collection='q.courseIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> </if>" +
            "GROUP BY w.course_id, sc.course_name ORDER BY wrongCount DESC" +
            "</script>")
    List<Map<String, Object>> statsCourseByTeacher(@Param("q") TeacherWrongQuery q);

    /** 教师端：高频错题 Top10（哪些题错得最多，讲评薄弱点用） */
    @Select("<script>" +
            "SELECT w.question_id AS questionId, q.content AS content, COUNT(*) AS wrongCount, " +
            "COUNT(DISTINCT w.user_id) AS studentCount " +
            "FROM wrong_question w LEFT JOIN question q ON w.question_id = q.id " +
            "JOIN sys_user u ON w.user_id = u.id WHERE 1=1 " +
            "<if test='q.classId != null'> AND u.class_id = #{q.classId} </if>" +
            "<if test='q.courseId != null'> AND w.course_id = #{q.courseId} </if>" +
            "<if test='q.courseIds != null'> AND w.course_id IN " +
            "<foreach collection='q.courseIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> </if>" +
            "<if test='q.questionType != null'> AND w.question_type = #{q.questionType} </if>" +
            "GROUP BY w.question_id, q.content ORDER BY wrongCount DESC LIMIT 10" +
            "</script>")
    List<Map<String, Object>> statsTopQuestionsByTeacher(@Param("q") TeacherWrongQuery q);
}
