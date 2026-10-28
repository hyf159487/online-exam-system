package com.exam.exam_system.mapper;

import com.exam.exam_system.dto.MonitorStudentVO;
import com.exam.exam_system.dto.RunningExamVO;
import com.exam.exam_system.dto.StudentAnswerDetailVO;
import com.exam.exam_system.entity.ExamStudent;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 考试监控 Mapper
 */
@Mapper
public interface ExamMonitorMapper {

    /**
     * 获取当前正在进行中的考试（start_time <= NOW <= end_time AND status=1）
     * 支持教师（只看自己创建的）和管理员（看全部）
     */
    @Select("<script>" +
            "SELECT e.id, e.exam_name AS examName, e.course_id AS courseId, c.course_name AS courseName, " +
            "e.paper_id AS paperId, p.paper_name AS paperName, " +
            "e.start_time AS startTime, e.end_time AS endTime, e.duration, " +
            "COUNT(DISTINCT es.student_id) AS studentCount, " +
            "SUM(CASE WHEN es.status = 1 THEN 1 ELSE 0 END) AS activeCount " +
            "FROM exam e " +
            "LEFT JOIN sys_course c ON e.course_id = c.id " +
            "LEFT JOIN exam_paper p ON e.paper_id = p.id " +
            "LEFT JOIN exam_student es ON es.exam_id = e.id " +
            "WHERE e.start_time &lt;= NOW() AND e.end_time &gt;= NOW() AND e.status = 1 " +
            "<if test='courseIds != null'>AND e.course_id IN " +
            "<foreach collection='courseIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> </if>" +
            "GROUP BY e.id " +
            "ORDER BY e.start_time DESC" +
            "</script>")
    List<RunningExamVO> listRunningExams(@Param("courseIds") List<Long> courseIds);

    /**
     * 分页查询某场考试的学生监控列表
     */
    @SelectProvider(type = ExamMonitorMapper.MonitorSqlProvider.class, method = "listStudents")
    List<MonitorStudentVO> listStudents(@Param("examId") Long examId,
                                         @Param("classId") Long classId,
                                         @Param("status") Integer status,
                                         @Param("keyword") String keyword,
                                         @Param("offset") int offset,
                                         @Param("pageSize") int pageSize);

    @SelectProvider(type = ExamMonitorMapper.MonitorSqlProvider.class, method = "countStudents")
    long countStudents(@Param("examId") Long examId,
                       @Param("classId") Long classId,
                       @Param("status") Integer status,
                       @Param("keyword") String keyword);

    /**
     * 查看某个学生的答题详情
     */
    @Select("SELECT q.id AS questionId, q.content, q.question_type AS questionType, " +
            "q.options, ea.user_answer AS userAnswer, q.answer AS correctAnswer, " +
            "ea.is_correct AS isCorrect, ea.get_score AS score, " +
            "pq.sort_order AS sortOrder, " +
            "CASE WHEN ea.id IS NOT NULL THEN TRUE ELSE FALSE END AS answered " +
            "FROM paper_question pq " +
            "JOIN question q ON pq.question_id = q.id " +
            "LEFT JOIN exam_record r ON r.paper_id = (SELECT paper_id FROM exam WHERE id = #{examId}) " +
            "AND r.user_id = #{studentId} AND r.status < 2 " +
            "LEFT JOIN exam_answer ea ON ea.record_id = r.id AND ea.question_id = q.id " +
            "WHERE pq.paper_id = (SELECT paper_id FROM exam WHERE id = #{examId}) " +
            "ORDER BY pq.sort_order")
    List<StudentAnswerDetailVO> listStudentAnswers(@Param("examId") Long examId,
                                                    @Param("studentId") Long studentId);

    /**
     * 插入 exam_student 记录（学生进入考试时）
     */
    @Insert("INSERT INTO exam_student(exam_id, student_id, status, answered_count, total_count, " +
            "remaining_time, screen_switch_count, enter_time) " +
            "VALUES(#{examId}, #{studentId}, #{status}, #{answeredCount}, #{totalCount}, " +
            "#{remainingTime}, #{screenSwitchCount}, #{enterTime}) " +
            "ON DUPLICATE KEY UPDATE status = #{status}, update_time = CURRENT_TIMESTAMP")
    int insertOrUpdate(ExamStudent es);

    /**
     * 更新 exam_student 状态（交卷/强制交卷时）
     */
    @Update("UPDATE exam_student SET status = #{status}, leave_time = NOW(), update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 按 exam_id + student_id 查 exam_student
     */
    @Select("SELECT * FROM exam_student WHERE exam_id = #{examId} AND student_id = #{studentId} LIMIT 1")
    ExamStudent findByExamAndStudent(@Param("examId") Long examId, @Param("studentId") Long studentId);

    /**
     * 更新 exam_student 进度
     */
    @Update("UPDATE exam_student SET answered_count = #{answeredCount}, remaining_time = #{remainingTime}, " +
            "screen_switch_count = #{screenSwitchCount}, update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    int updateProgress(@Param("id") Long id,
                        @Param("answeredCount") Integer answeredCount,
                        @Param("remainingTime") Integer remainingTime,
                        @Param("screenSwitchCount") Integer screenSwitchCount);

    /** 删除某场考试的全部监控记录（删除考试时级联清理） */
    @Delete("DELETE FROM exam_student WHERE exam_id = #{examId}")
    int deleteByExamId(@Param("examId") Long examId);

    /** 内部类：动态 SQL */
    class MonitorSqlProvider {

        public String listStudents(@Param("examId") Long examId,
                                   @Param("classId") Long classId,
                                   @Param("status") Integer status,
                                   @Param("keyword") String keyword,
                                   @Param("offset") int offset,
                                   @Param("pageSize") int pageSize) {
            StringBuilder sb = new StringBuilder(
                    "SELECT es.id, es.exam_id AS examId, es.student_id AS studentId, " +
                    "es.status, es.answered_count AS answeredCount, es.total_count AS totalCount, " +
                    "es.remaining_time AS remainingTime, es.screen_switch_count AS screenSwitchCount, " +
                    "es.enter_time AS enterTime, es.leave_time AS leaveTime, " +
                    "u.username, u.real_name, u.class_id AS classId, " +
                    "c.class_name AS className " +
                    "FROM exam_student es " +
                    "LEFT JOIN sys_user u ON es.student_id = u.id " +
                    "LEFT JOIN sys_class c ON u.class_id = c.id " +
                    "WHERE es.exam_id = #{examId} ");
            if (classId != null) {
                sb.append("AND u.class_id = #{classId} ");
            }
            if (status != null) {
                sb.append("AND es.status = #{status} ");
            }
            if (keyword != null && !keyword.isBlank()) {
                sb.append("AND (u.username LIKE CONCAT('%', #{keyword}, '%') " +
                        "OR u.real_name LIKE CONCAT('%', #{keyword}, '%')) ");
            }
            sb.append("ORDER BY es.status ASC, es.enter_time DESC LIMIT #{pageSize} OFFSET #{offset}");
            return sb.toString();
        }

        public String countStudents(@Param("examId") Long examId,
                                     @Param("classId") Long classId,
                                     @Param("status") Integer status,
                                     @Param("keyword") String keyword) {
            StringBuilder sb = new StringBuilder(
                    "SELECT COUNT(*) FROM exam_student es " +
                    "LEFT JOIN sys_user u ON es.student_id = u.id " +
                    "LEFT JOIN sys_class c ON u.class_id = c.id " +
                    "WHERE es.exam_id = #{examId} ");
            if (classId != null) {
                sb.append("AND u.class_id = #{classId} ");
            }
            if (status != null) {
                sb.append("AND es.status = #{status} ");
            }
            if (keyword != null && !keyword.isBlank()) {
                sb.append("AND (u.username LIKE CONCAT('%', #{keyword}, '%') " +
                        "OR u.real_name LIKE CONCAT('%', #{keyword}, '%')) ");
            }
            return sb.toString();
        }
    }
}
