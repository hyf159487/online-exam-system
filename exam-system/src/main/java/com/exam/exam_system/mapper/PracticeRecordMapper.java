package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.PracticeRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 学生练习记录 Mapper
 */
@Mapper
public interface PracticeRecordMapper {

    /** 分页列表（教师端：教师看自己练习卷的记录，管理员看全部） */
    @SelectProvider(type = SqlProvider.class, method = "listPage")
    List<PracticeRecord> listPage(@Param("offset") int offset,
                                  @Param("pageSize") int pageSize,
                                  @Param("teacherId") Long teacherId,
                                  @Param("role") Integer role,
                                  @Param("paperId") Long paperId,
                                  @Param("studentId") Long studentId,
                                  @Param("keyword") String keyword,
                                  @Param("reviewStatus") Integer reviewStatus);

    @SelectProvider(type = SqlProvider.class, method = "countPage")
    long countPage(@Param("teacherId") Long teacherId,
                   @Param("role") Integer role,
                   @Param("paperId") Long paperId,
                   @Param("studentId") Long studentId,
                   @Param("keyword") String keyword,
                   @Param("reviewStatus") Integer reviewStatus);

    @Insert("INSERT INTO practice_record(practice_paper_id, student_id, student_name, score, total_score, " +
            "correct_count, wrong_count, finish_status, review_status, subjective_count, start_time, finish_time) " +
            "VALUES (#{practicePaperId}, #{studentId}, #{studentName}, #{score}, #{totalScore}, " +
            "#{correctCount}, #{wrongCount}, #{finishStatus}, #{reviewStatus}, #{subjectiveCount}, " +
            "#{startTime}, #{finishTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PracticeRecord r);

    /** 批改后回写：本次得分 / 对错题数 / 批改状态 */
    @Update("UPDATE practice_record SET score = #{score}, correct_count = #{correctCount}, " +
            "wrong_count = #{wrongCount}, review_status = #{reviewStatus} WHERE id = #{id}")
    int updateScoreReview(@Param("id") Long id,
                          @Param("score") Integer score,
                          @Param("correctCount") Integer correctCount,
                          @Param("wrongCount") Integer wrongCount,
                          @Param("reviewStatus") Integer reviewStatus);

    @Select("SELECT pr.*, pp.paper_name AS paperName, stu.class_id AS classId, " +
            "cls.class_name AS className " +
            "FROM practice_record pr " +
            "JOIN practice_paper pp ON pr.practice_paper_id = pp.id " +
            "LEFT JOIN sys_user stu ON pr.student_id = stu.id " +
            "LEFT JOIN sys_class cls ON stu.class_id = cls.id " +
            "WHERE pr.id = #{id}")
    PracticeRecord selectById(@Param("id") Long id);

    // ===== 文件型作业：学生提交附件（attachment_path 非空即视为文件型提交）=====

    /** 学生端：取我对某作业最新一条附件提交记录（未提交返回 null） */
    @Select("SELECT pr.* FROM practice_record pr " +
            "WHERE pr.practice_paper_id = #{paperId} AND pr.student_id = #{studentId} " +
            "AND pr.attachment_path IS NOT NULL ORDER BY pr.id DESC LIMIT 1")
    PracticeRecord selectMyAttachmentSubmit(@Param("paperId") Long paperId,
                                            @Param("studentId") Long studentId);

    /**
     * 学生提交作业文件：新增一条附件提交记录。
     * review_status = 1（待教师批改），finish_status = 1（学生已完成线下作答并提交）。
     */
    @Insert("INSERT INTO practice_record(practice_paper_id, student_id, student_name, score, total_score, " +
            "correct_count, wrong_count, finish_status, review_status, subjective_count, " +
            "attachment_name, attachment_path, attachment_size, attachment_type, submit_remark, " +
            "start_time, finish_time) VALUES (" +
            "#{practicePaperId}, #{studentId}, #{studentName}, 0, #{totalScore}, 0, 0, 1, 1, 0, " +
            "#{attachmentName}, #{attachmentPath}, #{attachmentSize}, #{attachmentType}, #{submitRemark}, " +
            "NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAttachmentSubmit(PracticeRecord r);

    /** 学生重新提交：覆盖附件与备注，清空上次批改结果并回到「待批改」（沿用同一条记录，教师端可追溯） */
    @Update("UPDATE practice_record SET attachment_name = #{attachmentName}, attachment_path = #{attachmentPath}, " +
            "attachment_size = #{attachmentSize}, attachment_type = #{attachmentType}, " +
            "submit_remark = #{submitRemark}, score = 0, review_status = 1, review_opinion = NULL, " +
            "review_user_id = NULL, review_time = NULL, finish_status = 1, finish_time = NOW() " +
            "WHERE id = #{id}")
    int updateAttachmentSubmit(PracticeRecord r);

    /** 教师批改文件型提交：给分 + 评语，标记「已批改」 */
    @Update("UPDATE practice_record SET score = #{score}, review_status = 2, " +
            "review_opinion = #{opinion}, review_user_id = #{teacherId}, review_time = NOW() " +
            "WHERE id = #{id}")
    int updateAttachmentReview(@Param("id") Long id,
                               @Param("score") Integer score,
                               @Param("opinion") String opinion,
                               @Param("teacherId") Long teacherId);

    /** 按ID批量查询（学生端列表批量回填「我的提交」详情，避免逐行查询） */
    @Select("<script>" +
            "SELECT pr.* FROM practice_record pr WHERE pr.id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<PracticeRecord> listByIds(@Param("ids") List<Long> ids);

    class SqlProvider {
        private String baseWhere(Long teacherId, Integer role,
                                 Long paperId, Long studentId, String keyword,
                                 Integer reviewStatus) {
            StringBuilder sb = new StringBuilder(
                    " FROM practice_record pr " +
                    "JOIN practice_paper pp ON pr.practice_paper_id = pp.id " +
                    "LEFT JOIN sys_user stu ON pr.student_id = stu.id " +
                    "LEFT JOIN sys_class cls ON stu.class_id = cls.id " +
                    "WHERE pp.is_deleted = 0 ");
            if (role != null && role == 1 && teacherId != null) {
                sb.append(" AND pp.create_teacher_id = #{teacherId} ");
            }
            if (paperId != null) {
                sb.append(" AND pr.practice_paper_id = #{paperId} ");
            }
            if (studentId != null) {
                sb.append(" AND pr.student_id = #{studentId} ");
            }
            // 批改作业工作台：按批改状态过滤（1待批改 2已批改 0无需批改）
            if (reviewStatus != null) {
                sb.append(" AND pr.review_status = #{reviewStatus} ");
            }
            if (keyword != null && !keyword.trim().isEmpty()) {
                sb.append(" AND (stu.real_name LIKE CONCAT('%', #{keyword}, '%') " +
                        " OR stu.user_name LIKE CONCAT('%', #{keyword}, '%') " +
                        " OR pp.paper_name LIKE CONCAT('%', #{keyword}, '%')) ");
            }
            return sb.toString();
        }

        public String listPage(int offset, int pageSize,
                               Long teacherId, Integer role,
                               Long paperId, Long studentId, String keyword,
                               Integer reviewStatus) {
            StringBuilder sb = new StringBuilder(
                    "SELECT pr.*, pp.paper_name AS paperName, cls.class_name AS className, " +
                    "(SELECT COUNT(*) FROM practice_answer pa " +
                    "  WHERE pa.practice_record_id = pr.id AND pa.is_objective = 0 " +
                    "    AND pa.review_status = 0) AS pendingReviewCount ");
            sb.append(baseWhere(teacherId, role, paperId, studentId, keyword, reviewStatus));
            sb.append(" ORDER BY pr.create_time DESC LIMIT #{offset}, #{pageSize}");
            return sb.toString();
        }

        public String countPage(Long teacherId, Integer role,
                                Long paperId, Long studentId, String keyword,
                                Integer reviewStatus) {
            StringBuilder sb = new StringBuilder("SELECT COUNT(*) ");
            sb.append(baseWhere(teacherId, role, paperId, studentId, keyword, reviewStatus));
            return sb.toString();
        }
    }
}
