package com.exam.exam_system.mapper;

import com.exam.exam_system.dto.ReviewWaitVO;
import com.exam.exam_system.entity.ExamAnswer;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 主观题阅卷 Mapper
 * 注意：通过 paper_id 关联 exam_record，因为原表无 exam_id 字段
 */
@Mapper
public interface ExamReviewMapper {

    /**
     * 分页查询主观题阅卷列表（待阅/已阅）
     */
    @SelectProvider(type = ExamReviewMapper.ReviewSqlProvider.class, method = "listSubjectiveAnswers")
    List<ReviewWaitVO> listSubjectiveAnswers(@Param("examId") Long examId,
                                               @Param("classId") Long classId,
                                               @Param("reviewStatus") Integer reviewStatus,
                                               @Param("keyword") String keyword,
                                               @Param("creatorId") Long creatorId,
                                               @Param("role") Integer role,
                                               @Param("offset") int offset,
                                               @Param("pageSize") int pageSize);

    @SelectProvider(type = ExamReviewMapper.ReviewSqlProvider.class, method = "countSubjectiveAnswers")
    long countSubjectiveAnswers(@Param("examId") Long examId,
                                 @Param("classId") Long classId,
                                 @Param("reviewStatus") Integer reviewStatus,
                                 @Param("keyword") String keyword,
                                 @Param("creatorId") Long creatorId,
                                 @Param("role") Integer role);

    /**
     * 按 ID 查询答题明细
     */
    @Select("SELECT ea.*, q.content, q.question_type AS questionType, q.answer AS correctAnswer, " +
            "pq.score AS questionScore, u.real_name, u.username, u.class_id AS classId, " +
            "c.class_name, e.exam_name AS examName, e.id AS examId " +
            "FROM exam_answer ea " +
            "JOIN question q ON ea.question_id = q.id " +
            "JOIN exam_record r ON ea.record_id = r.id " +
            // 题目分值必须取「本次考试所用试卷」上的分值：同一道题可能被多张试卷引用，
            // 不加 paper_id 限定会 JOIN 出多行，单对象查询就会抛
            // TooManyResultsException: Expected one result (or null) ... but found: N
            "LEFT JOIN paper_question pq ON pq.question_id = ea.question_id AND pq.paper_id = r.paper_id " +
            // exam 优先用 record.exam_id 关联：同一张试卷可被多场考试复用，用 paper_id 关联同样会取出多条
            "JOIN exam e ON e.id = COALESCE(r.exam_id, (SELECT MIN(e2.id) FROM exam e2 WHERE e2.paper_id = r.paper_id)) " +
            "JOIN sys_user u ON r.user_id = u.id " +
            "LEFT JOIN sys_class c ON u.class_id = c.id " +
            "WHERE ea.id = #{answerId}")
    ExamAnswer findDetailById(@Param("answerId") Long answerId);

    /**
     * 更新阅卷得分和状态
     */
    @Update("UPDATE exam_answer SET review_score = #{reviewScore}, review_opinion = #{reviewOpinion}, " +
            "review_status = 1, review_user_id = #{reviewUserId}, review_time = #{reviewTime}, " +
            "is_correct = CASE WHEN #{reviewScore} > 0 THEN 1 ELSE 0 END, " +
            "get_score = #{reviewScore} " +
            "WHERE id = #{id}")
    int updateReviewScore(@Param("id") Long id,
                           @Param("reviewScore") BigDecimal reviewScore,
                           @Param("reviewOpinion") String reviewOpinion,
                           @Param("reviewUserId") Long reviewUserId,
                           @Param("reviewTime") LocalDateTime reviewTime);

    /**
     * 查询某条考试记录的全部主观题已阅得分之和
     */
    @Select("SELECT COALESCE(SUM(review_score), 0) FROM exam_answer " +
            "WHERE record_id = #{recordId} AND is_objective = 0 AND review_status = 1")
    BigDecimal sumReviewedSubjectiveScore(@Param("recordId") Long recordId);

    /**
     * 查询某条考试记录的全部客观题自动得分之和
     */
    @Select("SELECT COALESCE(SUM(get_score), 0) FROM exam_answer " +
            "WHERE record_id = #{recordId} AND is_objective = 1")
    BigDecimal sumObjectiveScore(@Param("recordId") Long recordId);

    /**
     * 查询某条考试记录是否还有未批阅的主观题
     */
    @Select("SELECT COUNT(*) FROM exam_answer " +
            "WHERE record_id = #{recordId} AND is_objective = 0 AND review_status = 0")
    int countPendingSubjective(@Param("recordId") Long recordId);

    /**
     * 统计某场考试待阅卷的主观题数量（按考试维度）
     */
    @Select("SELECT COUNT(*) FROM exam_answer ea " +
            "JOIN exam_record r ON ea.record_id = r.id " +
            "WHERE r.paper_id = (SELECT e.paper_id FROM exam e WHERE e.id = #{examId}) " +
            "AND ea.is_objective = 0 AND ea.review_status = 0")
    int countPendingByExam(@Param("examId") Long examId);

    /** 内部类：动态 SQL */
    class ReviewSqlProvider {

        public String listSubjectiveAnswers(@Param("examId") Long examId,
                                              @Param("classId") Long classId,
                                              @Param("reviewStatus") Integer reviewStatus,
                                              @Param("keyword") String keyword,
                                              @Param("creatorId") Long creatorId,
                                              @Param("role") Integer role,
                                              @Param("offset") int offset,
                                              @Param("pageSize") int pageSize) {
            StringBuilder sb = new StringBuilder(
                    "SELECT ea.id AS answerId, ea.record_id AS recordId, ea.question_id AS questionId, " +
                    "q.content, q.question_type AS questionType, pq.score AS questionScore, " +
                    "ea.user_answer AS userAnswer, ea.review_score AS reviewScore, " +
                    "ea.review_opinion AS reviewOpinion, ea.review_status AS reviewStatus, " +
                    "r.id AS recordId2, r.submit_time, " +
                    "e.id AS examId, e.exam_name AS examName, " +
                    "u.id AS studentId, u.real_name, u.username, u.class_id AS classId2, " +
                    "c.class_name " +
                    "FROM exam_answer ea " +
                    "JOIN question q ON ea.question_id = q.id " +
                    "JOIN exam_record r ON ea.record_id = r.id " +
                    // 题目分值按「本场考试所用试卷」限定：同一道题被多张试卷引用时，
                    // 不带 paper_id 限定会让同一份答卷的行数翻倍（列表出现重复题目/错误分值）
                    "LEFT JOIN paper_question pq ON pq.question_id = ea.question_id AND pq.paper_id = r.paper_id " +
                    // exam 用 record.exam_id 关联：同一张试卷被多场考试复用时按 paper_id 关联会查出多场
                    "JOIN exam e ON e.id = COALESCE(r.exam_id, (SELECT MIN(e2.id) FROM exam e2 WHERE e2.paper_id = r.paper_id)) " +
                    "JOIN sys_user u ON r.user_id = u.id " +
                    "LEFT JOIN sys_class c ON u.class_id = c.id " +
                    "WHERE ea.is_objective = 0 " +
                    "AND r.status >= 1 ");
            if (examId != null) {
                sb.append("AND e.id = #{examId} ");
            }
            if (classId != null) {
                sb.append("AND u.class_id = #{classId} ");
            }
            if (reviewStatus != null) {
                sb.append("AND ea.review_status = #{reviewStatus} ");
            }
            if (keyword != null && !keyword.isBlank()) {
                sb.append("AND (u.username LIKE CONCAT('%', #{keyword}, '%') " +
                        "OR u.real_name LIKE CONCAT('%', #{keyword}, '%') " +
                        "OR q.content LIKE CONCAT('%', #{keyword}, '%')) ");
            }
            if (role != null && role == 1) {
                sb.append("AND e.creator_id = #{creatorId} ");
            }
            sb.append("ORDER BY ea.review_status ASC, r.submit_time DESC " +
                    "LIMIT #{pageSize} OFFSET #{offset}");
            return sb.toString();
        }

        public String countSubjectiveAnswers(@Param("examId") Long examId,
                                               @Param("classId") Long classId,
                                               @Param("reviewStatus") Integer reviewStatus,
                                               @Param("keyword") String keyword,
                                               @Param("creatorId") Long creatorId,
                                               @Param("role") Integer role) {
            StringBuilder sb = new StringBuilder(
                    "SELECT COUNT(*) FROM exam_answer ea " +
                    "JOIN question q ON ea.question_id = q.id " +
                    "JOIN exam_record r ON ea.record_id = r.id " +
                    // 与列表查询保持同一限定口径，避免同一道题被多张试卷引用时计数翻倍
                    "LEFT JOIN paper_question pq ON pq.question_id = ea.question_id AND pq.paper_id = r.paper_id " +
                    "JOIN exam e ON e.id = COALESCE(r.exam_id, (SELECT MIN(e2.id) FROM exam e2 WHERE e2.paper_id = r.paper_id)) " +
                    "JOIN sys_user u ON r.user_id = u.id " +
                    "LEFT JOIN sys_class c ON u.class_id = c.id " +
                    "WHERE ea.is_objective = 0 " +
                    "AND r.status >= 1 ");
            if (examId != null) {
                sb.append("AND e.id = #{examId} ");
            }
            if (classId != null) {
                sb.append("AND u.class_id = #{classId} ");
            }
            if (reviewStatus != null) {
                sb.append("AND ea.review_status = #{reviewStatus} ");
            }
            if (keyword != null && !keyword.isBlank()) {
                sb.append("AND (u.username LIKE CONCAT('%', #{keyword}, '%') " +
                        "OR u.real_name LIKE CONCAT('%', #{keyword}, '%') " +
                        "OR q.content LIKE CONCAT('%', #{keyword}, '%')) ");
            }
            if (role != null && role == 1) {
                sb.append("AND e.creator_id = #{creatorId} ");
            }
            return sb.toString();
        }
    }
}
