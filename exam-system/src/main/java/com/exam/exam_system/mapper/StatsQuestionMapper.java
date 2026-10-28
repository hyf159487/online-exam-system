package com.exam.exam_system.mapper;

import com.exam.exam_system.dto.QuestionQualityVO;
import com.exam.exam_system.dto.StatsExamVO;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 试题质量统计 Mapper
 */
@Mapper
public interface StatsQuestionMapper {

    /**
     * 获取可统计的考试列表（已结束且全部批阅完成）
     * 注意：通过 paper_id 关联 exam_record，因为原表无 exam_id 字段
     */
    @SelectProvider(type = StatsQuestionSqlProvider.class, method = "listExams")
    List<StatsExamVO> listExams(@Param("teacherId") Long teacherId,
                                @Param("role") Integer role);

    /**
     * 获取某考试下所有题目列表
     */
    @Select("SELECT q.id, q.content, q.question_type AS questionType, q.options, " +
            "q.answer, q.analysis, pq.score " +
            "FROM paper_question pq " +
            "JOIN question q ON pq.question_id = q.id " +
            "WHERE pq.paper_id = (SELECT e.paper_id FROM exam e WHERE e.id = #{examId}) " +
            "ORDER BY pq.sort_order")
    List<QuestionBasicInfo> listQuestionsByExam(@Param("examId") Long examId);

    /**
     * 统计某题目的作答总人数
     */
    @Select("SELECT COUNT(*) FROM exam_answer ea " +
            "JOIN exam_record r ON ea.record_id = r.id " +
            "WHERE r.paper_id = (SELECT e.paper_id FROM exam e WHERE e.id = #{examId}) " +
            "AND ea.question_id = #{questionId} AND r.status >= 1")
    int countTotalAnswer(@Param("examId") Long examId, @Param("questionId") Long questionId);

    /**
     * 统计某题目的答对人数
     */
    @Select("SELECT COUNT(*) FROM exam_answer ea " +
            "JOIN exam_record r ON ea.record_id = r.id " +
            "WHERE r.paper_id = (SELECT e.paper_id FROM exam e WHERE e.id = #{examId}) " +
            "AND ea.question_id = #{questionId} " +
            "AND r.status >= 1 AND (ea.is_correct = 1 OR ea.get_score > 0)")
    int countCorrectAnswer(@Param("examId") Long examId, @Param("questionId") Long questionId);

    /**
     * 统计某题目的平均得分
     */
    @Select("SELECT IFNULL(ROUND(AVG(ea.get_score), 2), 0) FROM exam_answer ea " +
            "JOIN exam_record r ON ea.record_id = r.id " +
            "WHERE r.paper_id = (SELECT e.paper_id FROM exam e WHERE e.id = #{examId}) " +
            "AND ea.question_id = #{questionId} " +
            "AND r.status >= 1 AND ea.get_score IS NOT NULL")
    BigDecimal avgScore(@Param("examId") Long examId, @Param("questionId") Long questionId);

    /**
     * 获取某题目的学生答题明细
     */
    @Select("SELECT u.id AS studentId, u.real_name AS realName, u.username, " +
            "ea.user_answer AS userAnswer, ea.is_correct AS isCorrect, ea.get_score AS getScore " +
            "FROM exam_answer ea " +
            "JOIN exam_record r ON ea.record_id = r.id " +
            "JOIN sys_user u ON r.user_id = u.id " +
            "WHERE r.paper_id = (SELECT e.paper_id FROM exam e WHERE e.id = #{examId}) " +
            "AND ea.question_id = #{questionId} AND r.status >= 1 " +
            "ORDER BY u.real_name")
    List<QuestionQualityVO.AnswerDetailItem> listAnswerDetails(@Param("examId") Long examId,
                                                               @Param("questionId") Long questionId);

    // ========== 内部类 ==========

    class QuestionBasicInfo {
        private Long id;
        private String content;
        private Integer questionType;
        private String options;
        private String answer;
        private String analysis;
        private BigDecimal score;

        public Long getId() { return id; }
        public void setId(Long v) { this.id = v; }
        public String getContent() { return content; }
        public void setContent(String v) { this.content = v; }
        public Integer getQuestionType() { return questionType; }
        public void setQuestionType(Integer v) { this.questionType = v; }
        public String getOptions() { return options; }
        public void setOptions(String v) { this.options = v; }
        public String getAnswer() { return answer; }
        public void setAnswer(String v) { this.answer = v; }
        public String getAnalysis() { return analysis; }
        public void setAnalysis(String v) { this.analysis = v; }
        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal v) { this.score = v; }
    }

    class StatsQuestionSqlProvider {

        public String listExams(@Param("teacherId") Long teacherId, @Param("role") Integer role) {
            StringBuilder sb = new StringBuilder(
                    "SELECT e.id, e.exam_name AS examName, c.course_name AS courseName, " +
                    "u.real_name AS creatorName, " +
                    "COUNT(DISTINCT r.user_id) AS studentCount, " +
                    "COUNT(DISTINCT CASE WHEN r.status >= 1 THEN r.user_id END) AS completedCount " +
                    "FROM exam e " +
                    "LEFT JOIN sys_course c ON e.course_id = c.id " +
                    "LEFT JOIN sys_user u ON e.creator_id = u.id " +
                    "LEFT JOIN exam_record r ON r.paper_id = e.paper_id " +  // 通过 paper_id 关联
                    "WHERE 1=1 ");
            if (role != null && role == 1) {
                sb.append("AND e.creator_id = #{teacherId} ");
            }
            sb.append("AND e.status = 1 AND e.end_time < NOW() " +  // 已发布且已结束
                    "GROUP BY e.id, e.exam_name, c.course_name, u.real_name " +
                    "HAVING completedCount > 0 " +
                    "ORDER BY e.create_time DESC");
            return sb.toString();
        }
    }
}
