package com.exam.exam_system.mapper;

import com.exam.exam_system.dto.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 班级学情统计 Mapper
 *
 * <p>成绩记录通过 exam.paper_id 关联 exam_record（兼容无 exam_id 的旧数据），
 * 名单口径：班级学生花名册 + 该试卷已交卷记录，支持缺考对照。</p>
 */
@Mapper
public interface StatsClassMapper {

    // ========== 考试 / 班级下拉 ==========

    /**
     * 获取可统计的考试列表（教师仅本人创建的考试，管理员全部）
     */
    @SelectProvider(type = StatsClassSqlProvider.class, method = "listExams")
    List<StatsExamVO> listExams(@Param("teacherId") Long teacherId,
                                @Param("role") Integer role);

    /**
     * 获取某考试下可统计的班级列表（含班级在册人数，供缺考对照）
     *
     * <p>班级来源 = 已加入本考试所属课程的学生所在班级 ∪ 已有交卷成绩的学生所在班级，
     * 这样「全班都缺考」的班级也能被选中统计。</p>
     */
    @Select("SELECT DISTINCT c.id AS classId, c.class_name AS className, " +
            "(SELECT COUNT(*) FROM sys_user uc WHERE uc.class_id = c.id AND uc.role = 0) AS classSize " +
            "FROM sys_class c " +
            "JOIN sys_user u ON u.class_id = c.id AND u.role = 0 " +
            "WHERE EXISTS (SELECT 1 FROM course_member cm WHERE cm.student_id = u.id " +
            "              AND cm.course_id = (SELECT e.course_id FROM exam e WHERE e.id = #{examId})) " +
            "   OR EXISTS (SELECT 1 FROM exam_record r WHERE r.user_id = u.id AND r.status >= 1 " +
            "              AND r.paper_id = (SELECT e2.paper_id FROM exam e2 WHERE e2.id = #{examId})) " +
            "ORDER BY c.class_name")
    List<ClassStatsQuery> listClassesByExam(@Param("examId") Long examId);

    /**
     * 判断考试是否属于指定教师（教师端越权校验用）
     */
    @Select("SELECT COUNT(*) FROM exam WHERE id = #{examId} AND creator_id = #{teacherId}")
    int countOwnedExam(@Param("examId") Long examId, @Param("teacherId") Long teacherId);

    // ========== 班级学情核心数据 ==========

    /**
     * 考试元信息：试卷 ID / 满分（优先试卷总分，兜底考试快照）/ 考试名 / 学科名 / 所属课程 ID
     */
    @Select("SELECT e.id AS examId, e.exam_name AS examName, " +
            "c.course_name AS courseName, e.course_id AS courseId, e.paper_id AS paperId, " +
            "COALESCE(NULLIF(p.total_score, 0), e.total_score, 0) AS fullScore " +
            "FROM exam e " +
            "LEFT JOIN exam_paper p ON p.id = e.paper_id " +
            "LEFT JOIN sys_course c ON c.id = e.course_id " +
            "WHERE e.id = #{examId}")
    ExamMetaRow getExamMeta(@Param("examId") Long examId);

    /**
     * 按班级查班级名
     */
    @Select("SELECT class_name FROM sys_class WHERE id = #{classId}")
    String selectClassName(@Param("classId") Long classId);

    /**
     * 班级学生花名册（学号正序）
     */
    @Select("SELECT id AS studentId, username, real_name AS realName " +
            "FROM sys_user WHERE class_id = #{classId} AND role = 0 ORDER BY username")
    List<StudentRow> listClassStudents(@Param("classId") Long classId);

    /**
     * 课程人数：已加入该考试所属课程的学生数
     * （只统计真正加入课程的学生——部分学生虽然在同一班级，但没有加入本课程，不参与本课程考试）
     */
    @Select("SELECT COUNT(*) FROM course_member cm " +
            "JOIN sys_user u ON u.id = cm.student_id " +
            "WHERE cm.course_id = #{courseId} AND u.role = 0")
    int countCourseMembers(@Param("courseId") Long courseId);

    /**
     * 应考名单：本班学生 ∩ 已加入该课程
     * （未加入课程的学生不参加本课程考试，不计入应考人数、也不进入缺考名单）
     */
    @Select("SELECT u.id AS studentId, u.username, u.real_name AS realName " +
            "FROM sys_user u " +
            "JOIN course_member cm ON cm.student_id = u.id AND cm.course_id = #{courseId} " +
            "WHERE u.class_id = #{classId} AND u.role = 0 " +
            "ORDER BY u.username")
    List<StudentRow> listClassStudentsInCourse(@Param("classId") Long classId,
                                               @Param("courseId") Long courseId);

    /**
     * 某试卷下所有已交卷成绩记录（含开始/提交时间，用于计算答题用时）
     */
    @Select("SELECT id, user_id AS userId, total_score AS totalScore, status, " +
            "start_time AS startTime, submit_time AS submitTime " +
            "FROM exam_record WHERE paper_id = #{paperId} AND status >= 1 " +
            "ORDER BY user_id, id")
    List<RecordRow> listExamRecordsByPaper(@Param("paperId") Long paperId);

    /**
     * 查询某学生对某试卷的一条已交卷记录（作答详情弹窗用）
     */
    @Select("SELECT r.id, r.user_id AS userId, r.total_score AS totalScore, r.status, " +
            "r.start_time AS startTime, r.submit_time AS submitTime, " +
            "u.real_name AS realName, u.username, p.total_score AS fullScore " +
            "FROM exam_record r " +
            "LEFT JOIN sys_user u ON r.user_id = u.id " +
            "LEFT JOIN exam_paper p ON r.paper_id = p.id " +
            "WHERE r.paper_id = #{paperId} AND r.user_id = #{studentId} AND r.status >= 1 " +
            "ORDER BY r.id DESC LIMIT 1")
    RecordRow findStudentRecord(@Param("paperId") Long paperId, @Param("studentId") Long studentId);

    /**
     * 试卷全部题目 + 该生逐题作答（LEFT JOIN，未作答题目也返回便于展示）
     */
    @Select("SELECT pq.sort_order AS sortOrder, q.id AS questionId, q.question_type AS questionType, " +
            "q.content, q.options, q.answer AS standardAnswer, q.analysis, " +
            "k.point_name AS knowledgeName, pq.score AS questionScore, " +
            "ea.user_answer AS userAnswer, ea.get_score AS getScore, ea.is_correct AS isCorrect " +
            "FROM paper_question pq " +
            "JOIN question q ON pq.question_id = q.id " +
            "LEFT JOIN knowledge_point k ON q.knowledge_id = k.id " +
            "LEFT JOIN exam_answer ea ON ea.question_id = pq.question_id AND ea.record_id = #{recordId} " +
            "WHERE pq.paper_id = #{paperId} " +
            "ORDER BY pq.sort_order, pq.id")
    List<AnswerRow> listPaperAnswers(@Param("recordId") Long recordId, @Param("paperId") Long paperId);

    // ========== 知识点正确率（不受试卷满分影响，比值口径） ==========

    @Select("SELECT q.knowledge_id AS knowledgeId, k.point_name AS knowledgeName, " +
            "COUNT(*) AS totalCount, " +
            "SUM(CASE WHEN ea.is_correct = 1 OR ea.get_score > 0 THEN 1 ELSE 0 END) AS correctCount " +
            "FROM exam_answer ea " +
            "JOIN exam_record r ON ea.record_id = r.id " +
            "JOIN sys_user u ON r.user_id = u.id " +
            "JOIN question q ON ea.question_id = q.id " +
            "LEFT JOIN knowledge_point k ON q.knowledge_id = k.id " +
            "WHERE r.paper_id = (SELECT e.paper_id FROM exam e WHERE e.id = #{examId}) " +
            "AND u.class_id = #{classId} AND r.status >= 1 " +
            "AND q.knowledge_id IS NOT NULL " +
            "GROUP BY q.knowledge_id, k.point_name " +
            "ORDER BY (SUM(CASE WHEN ea.is_correct = 1 OR ea.get_score > 0 THEN 1 ELSE 0 END) * 100.0 / COUNT(*)) ASC")
    List<KnowledgeStatsRow> listKnowledgeStats(@Param("examId") Long examId, @Param("classId") Long classId);

    // ========== 作答维度统计（题型 / 难度，右侧图表） ==========

    /**
     * 按题型统计：返回各题型（单选/多选/判断/填空/简答）的总作答数与答对数
     */
    @Select("SELECT q.question_type AS typeKey, " +
            "COUNT(*) AS totalCount, " +
            "SUM(CASE WHEN ea.is_correct = 1 OR ea.get_score > 0 THEN 1 ELSE 0 END) AS correctCount " +
            "FROM exam_answer ea " +
            "JOIN exam_record r ON ea.record_id = r.id " +
            "JOIN sys_user u ON r.user_id = u.id " +
            "JOIN question q ON ea.question_id = q.id " +
            "WHERE r.paper_id = (SELECT e.paper_id FROM exam e WHERE e.id = #{examId}) " +
            "AND u.class_id = #{classId} AND r.status >= 1 " +
            "AND q.question_type IS NOT NULL " +
            "GROUP BY q.question_type " +
            "ORDER BY q.question_type")
    List<DimensionStatsRow> listQuestionTypeStats(@Param("examId") Long examId, @Param("classId") Long classId);

    /**
     * 按难度统计：返回各难度档位（简单/中等/困难）的总作答数与答对数
     */
    @Select("SELECT q.difficulty AS typeKey, " +
            "COUNT(*) AS totalCount, " +
            "SUM(CASE WHEN ea.is_correct = 1 OR ea.get_score > 0 THEN 1 ELSE 0 END) AS correctCount " +
            "FROM exam_answer ea " +
            "JOIN exam_record r ON ea.record_id = r.id " +
            "JOIN sys_user u ON r.user_id = u.id " +
            "JOIN question q ON ea.question_id = q.id " +
            "WHERE r.paper_id = (SELECT e.paper_id FROM exam e WHERE e.id = #{examId}) " +
            "AND u.class_id = #{classId} AND r.status >= 1 " +
            "AND q.difficulty IS NOT NULL " +
            "GROUP BY q.difficulty " +
            "ORDER BY q.difficulty")
    List<DimensionStatsRow> listDifficultyStats(@Param("examId") Long examId, @Param("classId") Long classId);

    // ========== 历史遗留方法（StatsExport 报表导出页仍在使用，保留不动） ==========

    /**
     * 参考人数
     */
    @Select("SELECT COUNT(*) FROM exam_record r " +
            "JOIN sys_user u ON r.user_id = u.id " +
            "WHERE r.paper_id = (SELECT e.paper_id FROM exam e WHERE e.id = #{examId}) " +
            "AND u.class_id = #{classId} AND r.status >= 1")
    int countStudents(@Param("examId") Long examId, @Param("classId") Long classId);

    /**
     * 平均分
     */
    @Select("SELECT IFNULL(ROUND(AVG(r.total_score), 2), 0) FROM exam_record r " +
            "JOIN sys_user u ON r.user_id = u.id " +
            "WHERE r.paper_id = (SELECT e.paper_id FROM exam e WHERE e.id = #{examId}) " +
            "AND u.class_id = #{classId} AND r.status >= 1")
    BigDecimal avgScore(@Param("examId") Long examId, @Param("classId") Long classId);

    /**
     * 最高分
     */
    @Select("SELECT IFNULL(MAX(r.total_score), 0) FROM exam_record r " +
            "JOIN sys_user u ON r.user_id = u.id " +
            "WHERE r.paper_id = (SELECT e.paper_id FROM exam e WHERE e.id = #{examId}) " +
            "AND u.class_id = #{classId} AND r.status >= 1")
    BigDecimal maxScore(@Param("examId") Long examId, @Param("classId") Long classId);

    /**
     * 最低分
     */
    @Select("SELECT IFNULL(MIN(r.total_score), 0) FROM exam_record r " +
            "JOIN sys_user u ON r.user_id = u.id " +
            "WHERE r.paper_id = (SELECT e.paper_id FROM exam e WHERE e.id = #{examId}) " +
            "AND u.class_id = #{classId} AND r.status >= 1")
    BigDecimal minScore(@Param("examId") Long examId, @Param("classId") Long classId);

    /**
     * 及格人数（旧报表按 exam.pass_score，保留兼容）
     */
    @Select("SELECT COUNT(*) FROM exam_record r " +
            "JOIN sys_user u ON r.user_id = u.id " +
            "JOIN exam e ON e.id = #{examId} " +
            "WHERE r.paper_id = e.paper_id " +
            "AND u.class_id = #{classId} " +
            "AND r.status >= 1 AND r.total_score >= e.pass_score")
    int countPass(@Param("examId") Long examId, @Param("classId") Long classId);

    /**
     * 获取及格分数线
     */
    @Select("SELECT pass_score FROM exam WHERE id = #{examId}")
    BigDecimal getPassScore(@Param("examId") Long examId);

    /**
     * 固定分数段（0-59/60-79/80-100，供报表导出使用）
     *
     * <p>注意别名必须用驼峰：本工程开启了 map-underscore-to-camel-case，
     * MyBatis 会把列名去掉下划线后再匹配属性（range0_59 → range059），
     * 与属性名 range0_59 对不上，结果字段恒为 null（分数段统计恒为 0）。</p>
     */
    @Select("SELECT " +
            "COALESCE(SUM(CASE WHEN r.total_score < 60 THEN 1 ELSE 0 END), 0) AS range0To59, " +
            "COALESCE(SUM(CASE WHEN r.total_score >= 60 AND r.total_score < 80 THEN 1 ELSE 0 END), 0) AS range60To79, " +
            "COALESCE(SUM(CASE WHEN r.total_score >= 80 THEN 1 ELSE 0 END), 0) AS range80To100 " +
            "FROM exam_record r " +
            "JOIN sys_user u ON r.user_id = u.id " +
            "WHERE r.paper_id = (SELECT e.paper_id FROM exam e WHERE e.id = #{examId}) " +
            "AND u.class_id = #{classId} AND r.status >= 1")
    ScoreRangeCounts countScoreRanges(@Param("examId") Long examId, @Param("classId") Long classId);

    // ========== 内部行对象 ==========

    class ExamMetaRow {
        private Long examId;
        private String examName;
        private String courseName;
        private Long courseId;
        private Long paperId;
        private BigDecimal fullScore;

        public Long getExamId() { return examId; }
        public void setExamId(Long v) { this.examId = v; }
        public String getExamName() { return examName; }
        public void setExamName(String v) { this.examName = v; }
        public String getCourseName() { return courseName; }
        public void setCourseName(String v) { this.courseName = v; }
        public Long getCourseId() { return courseId; }
        public void setCourseId(Long v) { this.courseId = v; }
        public Long getPaperId() { return paperId; }
        public void setPaperId(Long v) { this.paperId = v; }
        public BigDecimal getFullScore() { return fullScore; }
        public void setFullScore(BigDecimal v) { this.fullScore = v; }
    }

    class StudentRow {
        private Long studentId;
        private String username;
        private String realName;

        public Long getStudentId() { return studentId; }
        public void setStudentId(Long v) { this.studentId = v; }
        public String getUsername() { return username; }
        public void setUsername(String v) { this.username = v; }
        public String getRealName() { return realName; }
        public void setRealName(String v) { this.realName = v; }
    }

    class RecordRow {
        private Long id;
        private Long userId;
        private BigDecimal totalScore;
        private Integer status;
        private LocalDateTime startTime;
        private LocalDateTime submitTime;
        private String realName;
        private String username;
        private BigDecimal fullScore;

        public Long getId() { return id; }
        public void setId(Long v) { this.id = v; }
        public Long getUserId() { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public BigDecimal getTotalScore() { return totalScore; }
        public void setTotalScore(BigDecimal v) { this.totalScore = v; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer v) { this.status = v; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime v) { this.startTime = v; }
        public LocalDateTime getSubmitTime() { return submitTime; }
        public void setSubmitTime(LocalDateTime v) { this.submitTime = v; }
        public String getRealName() { return realName; }
        public void setRealName(String v) { this.realName = v; }
        public String getUsername() { return username; }
        public void setUsername(String v) { this.username = v; }
        public BigDecimal getFullScore() { return fullScore; }
        public void setFullScore(BigDecimal v) { this.fullScore = v; }
    }

    class AnswerRow {
        private Integer sortOrder;
        private Long questionId;
        private Integer questionType;
        private String content;
        private String options;
        private String standardAnswer;
        private String analysis;
        private String knowledgeName;
        private BigDecimal questionScore;
        private String userAnswer;
        private BigDecimal getScore;
        private Integer isCorrect;

        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer v) { this.sortOrder = v; }
        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long v) { this.questionId = v; }
        public Integer getQuestionType() { return questionType; }
        public void setQuestionType(Integer v) { this.questionType = v; }
        public String getContent() { return content; }
        public void setContent(String v) { this.content = v; }
        public String getOptions() { return options; }
        public void setOptions(String v) { this.options = v; }
        public String getStandardAnswer() { return standardAnswer; }
        public void setStandardAnswer(String v) { this.standardAnswer = v; }
        public String getAnalysis() { return analysis; }
        public void setAnalysis(String v) { this.analysis = v; }
        public String getKnowledgeName() { return knowledgeName; }
        public void setKnowledgeName(String v) { this.knowledgeName = v; }
        public BigDecimal getQuestionScore() { return questionScore; }
        public void setQuestionScore(BigDecimal v) { this.questionScore = v; }
        public String getUserAnswer() { return userAnswer; }
        public void setUserAnswer(String v) { this.userAnswer = v; }
        public BigDecimal getGetScore() { return getScore; }
        public void setGetScore(BigDecimal v) { this.getScore = v; }
        public Integer getIsCorrect() { return isCorrect; }
        public void setIsCorrect(Integer v) { this.isCorrect = v; }
    }

    class ScoreRangeCounts {
        private Integer range0To59;
        private Integer range60To79;
        private Integer range80To100;

        public Integer getRange0To59() { return range0To59; }
        public void setRange0To59(Integer v) { this.range0To59 = v; }
        public Integer getRange60To79() { return range60To79; }
        public void setRange60To79(Integer v) { this.range60To79 = v; }
        public Integer getRange80To100() { return range80To100; }
        public void setRange80To100(Integer v) { this.range80To100 = v; }
    }

    class KnowledgeStatsRow {
        private Long knowledgeId;
        private String knowledgeName;
        private Integer totalCount;
        private Integer correctCount;

        public Long getKnowledgeId() { return knowledgeId; }
        public void setKnowledgeId(Long v) { this.knowledgeId = v; }
        public String getKnowledgeName() { return knowledgeName; }
        public void setKnowledgeName(String v) { this.knowledgeName = v; }
        public Integer getTotalCount() { return totalCount; }
        public void setTotalCount(Integer v) { this.totalCount = v; }
        public Integer getCorrectCount() { return correctCount; }
        public void setCorrectCount(Integer v) { this.correctCount = v; }
    }

    class DimensionStatsRow {
        private Integer typeKey;
        private Integer totalCount;
        private Integer correctCount;

        public Integer getTypeKey() { return typeKey; }
        public void setTypeKey(Integer v) { this.typeKey = v; }
        public Integer getTotalCount() { return totalCount; }
        public void setTotalCount(Integer v) { this.totalCount = v; }
        public Integer getCorrectCount() { return correctCount; }
        public void setCorrectCount(Integer v) { this.correctCount = v; }
    }

    class StatsClassSqlProvider {

        public String listExams(@Param("teacherId") Long teacherId, @Param("role") Integer role) {
            StringBuilder sb = new StringBuilder(
                    "SELECT e.id, e.exam_name AS examName, " +
                    "e.course_id AS courseId, c.course_name AS courseName, e.end_time AS endTime, " +
                    "u.real_name AS creatorName, " +
                    "COUNT(DISTINCT r.user_id) AS studentCount, " +
                    "COUNT(DISTINCT CASE WHEN r.status = 2 THEN r.user_id END) AS completedCount " +
                    "FROM exam e " +
                    "LEFT JOIN sys_course c ON e.course_id = c.id " +
                    "LEFT JOIN sys_user u ON e.creator_id = u.id " +
                    "LEFT JOIN exam_record r ON r.paper_id = e.paper_id AND r.status >= 1 " +  // 通过 paper_id 关联
                    "WHERE 1=1 ");
            if (role != null && role == 1) {
                sb.append("AND e.creator_id = #{teacherId} ");
            }
            sb.append("AND e.status = 1 AND e.end_time < NOW() " +
                    "GROUP BY e.id, e.exam_name, e.course_id, c.course_name, e.end_time, u.real_name " +
                    "HAVING studentCount > 0 " +
                    "ORDER BY e.create_time DESC");
            return sb.toString();
        }
    }
}
