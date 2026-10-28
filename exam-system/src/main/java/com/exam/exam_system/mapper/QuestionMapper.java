package com.exam.exam_system.mapper;

import com.exam.exam_system.dto.QuestionQuery;
import com.exam.exam_system.entity.Question;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 题目 Mapper，对应 question 表
 *
 * <p>复杂查询（带可选条件）用 @SelectProvider + 内部 SqlProvider 拼 SQL
 * 简单查询用 @Select 注解
 */
@Mapper
public interface QuestionMapper {

    @SelectProvider(type = QuestionMapper.QuestionSqlProvider.class, method = "listByQuery")
    List<Question> listByQuery(@Param("q") QuestionQuery q);

    @SelectProvider(type = QuestionMapper.QuestionSqlProvider.class, method = "countByQuery")
    long countByQuery(@Param("q") QuestionQuery q);

    @Select("SELECT q.id, q.course_id AS courseId, c.course_name AS courseName, " +
            "q.question_type AS questionType, q.content, q.options, q.answer, q.analysis, " +
            "q.difficulty, q.knowledge_id AS knowledgeId, q.score, q.creator_id AS creatorId, " +
            "q.create_time AS createTime, q.update_time AS updateTime, q.audit_status AS auditStatus, " +
            "q.audit_opinion AS auditOpinion, q.audit_time AS auditTime, q.audit_user_id AS auditUserId, " +
            "creator.username AS submitterName, auditor.username AS auditorName, " +
            "kp.point_name AS knowledgeName, " +
            "(SELECT GROUP_CONCAT(qk.knowledge_id ORDER BY qk.id) FROM question_knowledge qk " +
            " WHERE qk.question_id = q.id) AS knowledgeIdsCsv, " +
            "(SELECT GROUP_CONCAT(kp2.point_name ORDER BY qk2.id SEPARATOR '、') FROM question_knowledge qk2 " +
            " JOIN knowledge_point kp2 ON kp2.id = qk2.knowledge_id WHERE qk2.question_id = q.id) AS knowledgeNames " +
            "FROM question q " +
            "LEFT JOIN sys_course c ON q.course_id = c.id " +
            "LEFT JOIN knowledge_point kp ON q.knowledge_id = kp.id " +
            "LEFT JOIN sys_user creator ON q.creator_id = creator.id " +
            "LEFT JOIN sys_user auditor ON q.audit_user_id = auditor.id " +
            "WHERE q.id = #{id}")
    Question selectById(@Param("id") Long id);

    /**
     * 批量查这些题目各自所属的课程 id（去重）。
     *
     * <p>用于批量改/删题目的数据权限校验：一次性拿到涉及的所有课程，
     * 再逐个判断是否属于当前教师任教的课程，避免逐条查库。
     */
    @Select("<script>SELECT DISTINCT course_id FROM question WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<Long> selectCourseIdsByIds(@Param("ids") List<Long> ids);

    /**
     * 新增题目
     * useGeneratedKeys=true + keyProperty="id" 让插入后 q.id 自动回填新主键
     */
    @Insert("INSERT INTO question(course_id, question_type, content, options, answer, analysis, " +
            "difficulty, knowledge_id, score, creator_id, audit_status) " +
            "VALUES(#{courseId}, #{questionType}, #{content}, #{options}, #{answer}, #{analysis}, " +
            "#{difficulty}, #{knowledgeId}, #{score}, #{creatorId}, #{auditStatus})")
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Question q);

    /** 更新题目（按 id），重置为待审核 */
    @Update("UPDATE question SET " +
            "course_id=#{courseId}, question_type=#{questionType}, content=#{content}, " +
            "options=#{options}, answer=#{answer}, analysis=#{analysis}, " +
            "difficulty=#{difficulty}, knowledge_id=#{knowledgeId}, score=#{score}, " +
            "audit_status=0, audit_opinion=NULL, audit_time=NULL, audit_user_id=NULL " +
            "WHERE id=#{id}")
    int updateById(Question q);

    /** 删除题目（按 id） */
    @Delete("DELETE FROM question WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /** 批量删除题目（按 id 列表） */
    @Delete("<script>" +
            "DELETE FROM question WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int batchDeleteByIds(@Param("ids") List<Long> ids);

    /**
     * 批量设置题目知识点（仅更新 knowledge_id 与 update_time，不重置审核状态）。
     * 用于给历史未设置知识点的题目统一补齐关联。
     */
    @Update("<script>" +
            "UPDATE question SET knowledge_id=#{knowledgeId}, update_time=NOW() " +
            "WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int updateKnowledgeBatch(@Param("ids") List<Long> ids, @Param("knowledgeId") Long knowledgeId);

    // ==================== 题目-知识点关联表（question_knowledge，支持一题多知识点） ====================

    /** 删除某题全部知识点关联（knowledge_id 主字段不在这里更新，由 Service 统一维护） */
    @Delete("DELETE FROM question_knowledge WHERE question_id = #{questionId}")
    int deleteRelsByQuestionId(@Param("questionId") Long questionId);

    /** 批量删除若干题的全部知识点关联 */
    @Delete("<script>" +
            "DELETE FROM question_knowledge WHERE question_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int deleteRelsByQuestionIds(@Param("ids") List<Long> ids);

    /** 为某题插入一组知识点关联（调用前需先 deleteRels 清空） */
    @Insert("<script>" +
            "INSERT INTO question_knowledge(question_id, knowledge_id) VALUES " +
            "<foreach collection='knowledgeIds' item='kid' separator=','>(#{questionId}, #{kid})</foreach>" +
            "</script>")
    int insertRels(@Param("questionId") Long questionId, @Param("knowledgeIds") List<Long> knowledgeIds);

    /**
     * 批量新增题目
     * useGeneratedKeys=true + keyProperty="id" 让每条记录的自增 id 回填到对象
     */
    @Insert("<script>" +
            "INSERT INTO question(course_id, question_type, content, options, answer, analysis, " +
            "difficulty, knowledge_id, score, creator_id, audit_status) VALUES " +
            "<foreach collection='list' item='q' separator=','>" +
            "(#{q.courseId}, #{q.questionType}, #{q.content}, #{q.options}, #{q.answer}, " +
            "#{q.analysis}, #{q.difficulty}, #{q.knowledgeId}, #{q.score}, #{q.creatorId}, #{q.auditStatus})" +
            "</foreach>" +
            "</script>")
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int batchInsert(@Param("list") List<Question> list);

    /**
     * 学生自由刷题随机抽题：按学科 + 题型（多选）+ 难度（多选）+ 可选知识点，随机抽取 count 道题。
     * 返回 answer / analysis —— 做题过程中可在「答题 / 背题」间来回切换，背题时需要直接展示答案与解析。
     * ORDER BY RAND() 保证每次抽到的题目组合不同。
     */
    @Select("<script>" +
            "SELECT id, course_id AS courseId, question_type AS questionType, content, options, answer, analysis, score, difficulty " +
            "FROM question WHERE audit_status = 1 " +
            "AND course_id IN (SELECT course_id FROM course_member WHERE student_id = #{studentId}) " +
            "<if test='courseId != null'>AND course_id = #{courseId} </if>" +
            "<if test='questionTypes != null and questionTypes.size() > 0'>" +
            "AND question_type IN <foreach collection='questionTypes' item='t' open='(' separator=',' close=')'>#{t}</foreach> " +
            "</if>" +
            "<if test='difficulties != null and difficulties.size() > 0'>" +
            "AND difficulty IN <foreach collection='difficulties' item='d' open='(' separator=',' close=')'>#{d}</foreach> " +
            "</if>" +
            "<if test='knowledgeId != null'>AND knowledge_id = #{knowledgeId} </if>" +
            "ORDER BY RAND() LIMIT #{count}" +
            "</script>")
    List<Question> randomPickForPractice(@Param("courseId") Long courseId,
                                         @Param("questionTypes") List<Integer> questionTypes,
                                         @Param("difficulties") List<Integer> difficulties,
                                         @Param("knowledgeId") Long knowledgeId,
                                         @Param("count") Integer count,
                                         @Param("studentId") Long studentId);

    /**
     * 随机组卷抽题：按学科 + 题型 + 难度（可选）随机抽取 N 道题
     * ORDER BY RAND() 在题库量级（数百~数千）下性能可接受，且保证每次组卷题目不同
     */
    @Select("<script>" +
            "SELECT id, course_id AS courseId, question_type AS questionType, content, options, " +
            "answer, analysis, difficulty, score, creator_id AS creatorId " +
            "FROM question WHERE audit_status = 1 " +
            "<if test='courseId != null'>AND course_id = #{courseId} </if>" +
            "<if test='questionType != null'>AND question_type = #{questionType} </if>" +
            "<if test='difficulty != null'>AND difficulty = #{difficulty} </if>" +
            "ORDER BY RAND() LIMIT #{count}" +
            "</script>")
    List<Question> randomPick(@Param("courseId") Long courseId,
                              @Param("questionType") Integer questionType,
                              @Param("difficulty") Integer difficulty,
                              @Param("count") Integer count);

    /** 按题型统计某学科下的题量（随机组卷校验题库是否充足），只统计审核通过的 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM question WHERE audit_status = 1 " +
            "<if test='courseId != null'>AND course_id = #{courseId} </if>" +
            "<if test='questionType != null'>AND question_type = #{questionType} </if>" +
            "<if test='difficulty != null'>AND difficulty = #{difficulty} </if>" +
            "</script>")
    long countByTypeAndDifficulty(@Param("courseId") Long courseId,
                                  @Param("questionType") Integer questionType,
                                  @Param("difficulty") Integer difficulty);

    // ==================== AI 智能组卷：题库统计 + 候选池 ====================

    /**
     * 按学科统计「题型 × 难度档」可用题量（仅审核通过的题目）。
     *
     * <p>难度档归一化：1=简单(difficulty 为 NULL 或 ≤1)、2=中等(=2)、3=困难(≥3)，
     * 兼容题库中 1~5 的难度取值，供智能组卷估算可抽题量。
     *
     * @return 每行含 questionType / difficultyLevel / cnt
     */
    @Select("SELECT question_type AS questionType, " +
            "CASE WHEN difficulty IS NULL OR difficulty <= 1 THEN 1 " +
            "     WHEN difficulty = 2 THEN 2 ELSE 3 END AS difficultyLevel, " +
            "COUNT(*) AS cnt " +
            "FROM question WHERE audit_status = 1 AND course_id = #{courseId} " +
            "GROUP BY question_type, difficultyLevel")
    List<Map<String, Object>> statByTypeAndLevel(@Param("courseId") Long courseId);

    /**
     * 学生刷题画像：按学科统计「题型 × 难度档」题量（仅审核通过的题目）。
     *
     * <p>难度档与前端刷题筛选的三档口径保持一致：1=简单(difficulty ≤ 2 或为空)、
     * 2=中等(= 3)、3=困难(≥ 4)；不能复用智能组卷的 {@link #statByTypeAndLevel(Long)}，
     * 那边的档位是给容量估算用的另一套口径，两者不能混用一个定义。</p>
     *
     * @return 每行含 questionType / level / cnt
     */
    @Select("SELECT question_type AS questionType, " +
            "CASE WHEN difficulty IS NULL OR difficulty <= 2 THEN 1 " +
            "     WHEN difficulty = 3 THEN 2 ELSE 3 END AS level, " +
            "COUNT(*) AS cnt " +
            "FROM question WHERE audit_status = 1 AND course_id = #{courseId} " +
            "GROUP BY question_type, level")
    List<Map<String, Object>> statTypeAndLevelForPractice(@Param("courseId") Long courseId);

    /**
     * 按学科统计各知识点的可用题量（仅审核通过），供「重点知识点」下拉展示题量。
     *
     * <p>知识点来源取并集：question_knowledge 关联表（一题多知识点）+ question.knowledge_id 主知识点，
     * 用 UNION 去重，避免同一「题-知识点」对被重复计数。
     *
     * @return 每行含 knowledgeId / knowledgeName / cnt
     */
    @Select("SELECT kp.id AS knowledgeId, kp.point_name AS knowledgeName, " +
            "COUNT(DISTINCT t.questionId) AS cnt FROM (" +
            "  SELECT qk.question_id AS questionId, qk.knowledge_id AS knowledgeId " +
            "  FROM question q JOIN question_knowledge qk ON qk.question_id = q.id " +
            "  WHERE q.audit_status = 1 AND q.course_id = #{courseId} " +
            "  UNION " +
            "  SELECT q.id AS questionId, q.knowledge_id AS knowledgeId FROM question q " +
            "  WHERE q.audit_status = 1 AND q.course_id = #{courseId} AND q.knowledge_id IS NOT NULL" +
            ") t JOIN knowledge_point kp ON kp.id = t.knowledgeId " +
            "GROUP BY kp.id, kp.point_name ORDER BY cnt DESC, kp.id")
    List<Map<String, Object>> statByKnowledge(@Param("courseId") Long courseId);

    /**
     * AI 智能组卷候选池：按学科 + 题型 + 难度区间（可选）+ 知识点（可选）拉取候选题目。
     *
     * <p>返回带知识点名称（一题多知识点用顿号拼接），供服务层做「知识点覆盖率择优」；
     * 排序用 ORDER BY RAND()，保证每次生成方案抽到的题目组合不同。
     *
     * @param minDifficulty 难度下限（null = 不限）
     * @param maxDifficulty 难度上限（null = 不限；上限为 1 时把难度为空的题目也视为简单题）
     * @param knowledgeIds  仅取命中这些知识点的题目（null/空 = 不限知识点）
     * @param limit         候选条数上限
     */
    @Select("<script>" +
            "SELECT q.id, q.course_id AS courseId, q.question_type AS questionType, q.content, q.options, " +
            "q.answer, q.analysis, q.difficulty, q.score, q.knowledge_id AS knowledgeId, " +
            "kp.point_name AS knowledgeName, " +
            "(SELECT GROUP_CONCAT(kp2.point_name ORDER BY qk2.id SEPARATOR '、') FROM question_knowledge qk2 " +
            " JOIN knowledge_point kp2 ON kp2.id = qk2.knowledge_id WHERE qk2.question_id = q.id) AS knowledgeNames " +
            "FROM question q " +
            "LEFT JOIN knowledge_point kp ON kp.id = q.knowledge_id " +
            "WHERE q.audit_status = 1 " +
            "<if test='courseId != null'>AND q.course_id = #{courseId} </if>" +
            "<if test='questionType != null'>AND q.question_type = #{questionType} </if>" +
            "<if test='minDifficulty != null'>AND q.difficulty &gt;= #{minDifficulty} </if>" +
            "<if test='maxDifficulty != null'>AND (q.difficulty IS NULL OR q.difficulty &lt;= #{maxDifficulty}) </if>" +
            "<if test='knowledgeIds != null and knowledgeIds.size() > 0'>" +
            "AND (q.knowledge_id IN " +
            "<foreach collection='knowledgeIds' item='kid' open='(' separator=',' close=')'>#{kid}</foreach>" +
            " OR EXISTS (SELECT 1 FROM question_knowledge qk WHERE qk.question_id = q.id AND qk.knowledge_id IN " +
            "<foreach collection='knowledgeIds' item='kid2' open='(' separator=',' close=')'>#{kid2}</foreach>" +
            ")) " +
            "</if>" +
            "ORDER BY RAND() LIMIT #{limit}" +
            "</script>")
    List<Question> listComposeCandidates(@Param("courseId") Long courseId,
                                         @Param("questionType") Integer questionType,
                                         @Param("minDifficulty") Integer minDifficulty,
                                         @Param("maxDifficulty") Integer maxDifficulty,
                                         @Param("knowledgeIds") List<Long> knowledgeIds,
                                         @Param("limit") Integer limit);

    /**
     * 驳回题目：设置 audit_status=2，保存驳回意见和审核人/时间
     */
    @Update("UPDATE question SET audit_status=2, audit_opinion=#{opinion}, " +
            "audit_time=NOW(), audit_user_id=#{auditorId} WHERE id=#{questionId}")
    int reject(@Param("questionId") Long questionId,
               @Param("opinion") String opinion,
               @Param("auditorId") Long auditorId);

    /**
     * 审核通过
     */
    @Update("UPDATE question SET audit_status=1, audit_opinion=NULL, " +
            "audit_time=NOW(), audit_user_id=#{auditorId} WHERE id=#{questionId}")
    int pass(@Param("questionId") Long questionId, @Param("auditorId") Long auditorId);

    /**
     * 查同学科题库中题干一致、已审核通过的正式题目 id 列表（审核通过前查重用，排除待审核题目自身）
     * 返回所有重复题 id，供前端定位；无重复返回空列表
     */
    @Select("SELECT id FROM question " +
            "WHERE course_id = #{courseId} AND audit_status = 1 " +
            "AND content = #{content} AND id != #{id} ORDER BY id")
    List<Long> listDuplicateIdsInBank(@Param("courseId") Long courseId,
                                      @Param("content") String content,
                                      @Param("id") Long id);

    /**
     * 重新提交审核（教师修改后重提）
     */
    @Update("UPDATE question SET audit_status=0, audit_opinion=NULL, " +
            "audit_time=NULL, audit_user_id=NULL WHERE id=#{questionId}")
    int resubmit(@Param("questionId") Long questionId);

    /**
     * 审核历史查询（audit_status IN (1, 2)，支持筛选通过/驳回、提交教师、审核人、题干关键词、时间范围）
     */
    @Select("<script>" +
            "SELECT q.*, c.course_name, " +
            "creator.username AS submitter_name, " +
            "auditor.username AS auditor_name " +
            "FROM question q " +
            "LEFT JOIN sys_course c ON q.course_id = c.id " +
            "LEFT JOIN sys_user creator ON q.creator_id = creator.id " +
            "LEFT JOIN sys_user auditor ON q.audit_user_id = auditor.id " +
            "WHERE q.audit_status IN (1, 2) " +
            "<if test='auditStatus != null'>AND q.audit_status = #{auditStatus} </if>" +
            "<if test='courseId != null'>AND q.course_id = #{courseId} </if>" +
            "<if test='questionType != null'>AND q.question_type = #{questionType} </if>" +
            "<if test='difficulty != null'>AND q.difficulty = #{difficulty} </if>" +
            "<if test='creatorId != null'>AND q.creator_id = #{creatorId} </if>" +
            "<if test='auditUserId != null'>AND q.audit_user_id = #{auditUserId} </if>" +
            "<if test='keyword != null and keyword != \"\"'>AND q.content LIKE CONCAT('%', #{keyword}, '%') </if>" +
            "<if test='startTime != null and startTime != \"\"'>AND q.create_time &gt;= #{startTime} </if>" +
            "<if test='endTime != null and endTime != \"\"'>AND q.create_time &lt;= #{endTime} </if>" +
            "ORDER BY q.audit_time DESC " +
            "LIMIT #{offset}, #{pageSize}" +
            "</script>")
    java.util.List<Question> listAuditHistory(@Param("auditStatus") Integer auditStatus,
                                              @Param("courseId") Long courseId,
                                              @Param("questionType") Integer questionType,
                                              @Param("difficulty") Integer difficulty,
                                              @Param("creatorId") Long creatorId,
                                              @Param("auditUserId") Long auditUserId,
                                              @Param("keyword") String keyword,
                                              @Param("startTime") String startTime,
                                              @Param("endTime") String endTime,
                                              @Param("offset") int offset,
                                              @Param("pageSize") int pageSize);

    /**
     * 审核历史计数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM question q " +
            "WHERE q.audit_status IN (1, 2) " +
            "<if test='auditStatus != null'>AND q.audit_status = #{auditStatus} </if>" +
            "<if test='courseId != null'>AND q.course_id = #{courseId} </if>" +
            "<if test='questionType != null'>AND q.question_type = #{questionType} </if>" +
            "<if test='difficulty != null'>AND q.difficulty = #{difficulty} </if>" +
            "<if test='creatorId != null'>AND q.creator_id = #{creatorId} </if>" +
            "<if test='auditUserId != null'>AND q.audit_user_id = #{auditUserId} </if>" +
            "<if test='keyword != null and keyword != \"\"'>AND q.content LIKE CONCAT('%', #{keyword}, '%') </if>" +
            "<if test='startTime != null and startTime != \"\"'>AND q.create_time &gt;= #{startTime} </if>" +
            "<if test='endTime != null and endTime != \"\"'>AND q.create_time &lt;= #{endTime} </if>" +
            "</script>")
    long countAuditHistory(@Param("auditStatus") Integer auditStatus,
                           @Param("courseId") Long courseId,
                           @Param("questionType") Integer questionType,
                           @Param("difficulty") Integer difficulty,
                           @Param("creatorId") Long creatorId,
                           @Param("auditUserId") Long auditUserId,
                           @Param("keyword") String keyword,
                           @Param("startTime") String startTime,
                           @Param("endTime") String endTime);

    /**
     * 内部类：动态 SQL 生成器
     */
    class QuestionSqlProvider {

        public String listByQuery(@Param("q") QuestionQuery q) {
            StringBuilder sb = new StringBuilder(
                    "SELECT q.id, q.course_id AS courseId, c.course_name AS courseName, " +
                    "q.question_type AS questionType, q.content, q.options, q.answer, q.analysis, " +
                    "q.difficulty, q.knowledge_id AS knowledgeId, q.score, q.creator_id AS creatorId, " +
                    "q.create_time AS createTime, q.update_time AS updateTime, q.audit_status AS auditStatus, " +
                    "q.audit_opinion AS auditOpinion, q.audit_time AS auditTime, q.audit_user_id AS auditUserId, " +
                    "creator.username AS submitterName, auditor.username AS auditorName, " +
                    "kp.point_name AS knowledgeName, " +
                    "(SELECT GROUP_CONCAT(qk.knowledge_id ORDER BY qk.id) FROM question_knowledge qk " +
                    " WHERE qk.question_id = q.id) AS knowledgeIdsCsv, " +
                    "(SELECT GROUP_CONCAT(kp2.point_name ORDER BY qk2.id SEPARATOR '、') FROM question_knowledge qk2 " +
                    " JOIN knowledge_point kp2 ON kp2.id = qk2.knowledge_id WHERE qk2.question_id = q.id) AS knowledgeNames " +
                    "FROM question q " +
                    "LEFT JOIN sys_course c ON q.course_id = c.id " +
                    "LEFT JOIN knowledge_point kp ON q.knowledge_id = kp.id " +
                    "LEFT JOIN sys_user creator ON q.creator_id = creator.id " +
                    "LEFT JOIN sys_user auditor ON q.audit_user_id = auditor.id " +
                    "WHERE 1=1 ");
            if (q.getId() != null) {
                sb.append("AND q.id = #{q.id} ");
            }
            if (q.getCourseId() != null) {
                sb.append("AND q.course_id = #{q.courseId} ");
            }
            if (q.getQuestionType() != null) {
                sb.append("AND q.question_type = #{q.questionType} ");
            }
            if (q.getDifficulty() != null) {
                sb.append("AND q.difficulty = #{q.difficulty} ");
            }
            if (q.getAuditStatus() != null) {
                sb.append("AND q.audit_status = #{q.auditStatus} ");
            }
            if (q.getCreatorId() != null) {
                sb.append("AND q.creator_id = #{q.creatorId} ");
            }
            // 数据权限：教师只能看到自己任教课程下的题目（由后端注入，见 TeacherScope）
            if (q.getCourseIds() != null && !q.getCourseIds().isEmpty()) {
                sb.append("AND q.course_id IN (");
                for (int i = 0; i < q.getCourseIds().size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append("#{q.courseIds[").append(i).append("]}");
                }
                sb.append(") ");
            }
            if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
                sb.append("AND q.content LIKE CONCAT('%', #{q.keyword}, '%') ");
            }
            if (q.getStartTime() != null && !q.getStartTime().isBlank()) {
                sb.append("AND q.create_time >= #{q.startTime} ");
            }
            if (q.getEndTime() != null && !q.getEndTime().isBlank()) {
                sb.append("AND q.create_time <= #{q.endTime} ");
            }
            String sortBy = q.getSortBy();
            if (sortBy != null && !sortBy.isBlank()) {
                String[] parts = sortBy.split("_");
                String field = parts[0];
                String order = parts.length > 1 ? parts[1].toUpperCase() : "DESC";
                String col = switch (field) {
                    case "course" -> "c.course_name";
                    case "questionType" -> "q.question_type";
                    case "difficulty" -> "q.difficulty";
                    case "score" -> "q.score";
                    case "auditStatus" -> "q.audit_status";
                    case "createTime" -> "q.create_time";
                    default -> "q.id";
                };
                sb.append("ORDER BY ").append(col).append(" ").append(order).append(" ");
            } else {
                sb.append("ORDER BY q.id DESC ");
            }
            sb.append("LIMIT #{q.pageSize} OFFSET #{q.offset}");
            return sb.toString();
        }

        public String countByQuery(@Param("q") QuestionQuery q) {
            StringBuilder sb = new StringBuilder(
                    "SELECT COUNT(*) FROM question q WHERE 1=1 ");
            if (q.getId() != null) {
                sb.append("AND q.id = #{q.id} ");
            }
            if (q.getCourseId() != null) {
                sb.append("AND q.course_id = #{q.courseId} ");
            }
            if (q.getQuestionType() != null) {
                sb.append("AND q.question_type = #{q.questionType} ");
            }
            if (q.getDifficulty() != null) {
                sb.append("AND q.difficulty = #{q.difficulty} ");
            }
            if (q.getAuditStatus() != null) {
                sb.append("AND q.audit_status = #{q.auditStatus} ");
            }
            if (q.getCreatorId() != null) {
                sb.append("AND q.creator_id = #{q.creatorId} ");
            }
            // 数据权限：教师只能看到自己任教课程下的题目（由后端注入，见 TeacherScope）
            if (q.getCourseIds() != null && !q.getCourseIds().isEmpty()) {
                sb.append("AND q.course_id IN (");
                for (int i = 0; i < q.getCourseIds().size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append("#{q.courseIds[").append(i).append("]}");
                }
                sb.append(") ");
            }
            if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
                sb.append("AND q.content LIKE CONCAT('%', #{q.keyword}, '%') ");
            }
            if (q.getStartTime() != null && !q.getStartTime().isBlank()) {
                sb.append("AND q.create_time >= #{q.startTime} ");
            }
            if (q.getEndTime() != null && !q.getEndTime().isBlank()) {
                sb.append("AND q.create_time <= #{q.endTime} ");
            }
            return sb.toString();
        }
    }
}
