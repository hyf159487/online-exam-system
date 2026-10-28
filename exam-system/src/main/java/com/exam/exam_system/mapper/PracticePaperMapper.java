package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.PracticePaper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 练习卷主表 Mapper
 */
@Mapper
public interface PracticePaperMapper {

    @Insert("INSERT INTO practice_paper(paper_name, course_id, description, total_score, question_count, " +
            "status, create_teacher_id, create_teacher_name, " +
            "attachment_name, attachment_path, attachment_size, attachment_type) " +
            "VALUES (#{paperName}, #{courseId}, #{description}, #{totalScore}, #{questionCount}, " +
            "#{status}, #{createTeacherId}, #{createTeacherName}, " +
            "#{attachmentName}, #{attachmentPath}, #{attachmentSize}, #{attachmentType})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PracticePaper p);

    @Update("UPDATE practice_paper SET paper_name = #{paperName}, course_id = #{courseId}, " +
            "description = #{description}, total_score = #{totalScore}, question_count = #{questionCount}, " +
            "attachment_name = #{attachmentName}, attachment_path = #{attachmentPath}, " +
            "attachment_size = #{attachmentSize}, attachment_type = #{attachmentType} " +
            "WHERE id = #{id}")
    int updateById(PracticePaper p);

    @Select("SELECT pp.*, c.course_name AS courseName, " +
            "(SELECT COUNT(*) FROM practice_record r WHERE r.practice_paper_id = pp.id) AS hasPracticeRecords " +
            "FROM practice_paper pp " +
            "LEFT JOIN sys_course c ON pp.course_id = c.id " +
            "WHERE pp.id = #{id} AND pp.is_deleted = 0")
    PracticePaper selectById(@Param("id") Long id);

    @Update("UPDATE practice_paper SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE practice_paper SET is_deleted = 1 WHERE id = #{id}")
    int logicDelete(@Param("id") Long id);

    // ===== 分页列表 =====
    @SelectProvider(type = SqlProvider.class, method = "listPage")
    List<PracticePaper> listPage(@Param("offset") int offset,
                                 @Param("pageSize") int pageSize,
                                 @Param("teacherId") Long teacherId,
                                 @Param("role") Integer role,
                                 @Param("keyword") String keyword,
                                 @Param("courseId") Long courseId,
                                 @Param("status") Integer status,
                                 @Param("studentId") Long studentId);

    @SelectProvider(type = SqlProvider.class, method = "countPage")
    long countPage(@Param("teacherId") Long teacherId,
                   @Param("role") Integer role,
                   @Param("keyword") String keyword,
                   @Param("courseId") Long courseId,
                   @Param("status") Integer status,
                   @Param("studentId") Long studentId);

    class SqlProvider {
        private String baseWhere(String tableAlias,
                                 Long teacherId, Integer role,
                                 String keyword, Long courseId, Integer status,
                                 Long studentId) {
            StringBuilder sb = new StringBuilder(" WHERE " + tableAlias + ".is_deleted = 0 ");
            // 教师只能看自己创建的；管理员看全部
            if (role != null && role == 1 && teacherId != null) {
                sb.append(" AND ").append(tableAlias).append(".create_teacher_id = #{teacherId} ");
            }
            // 学生端：只能看到已加入课程的练习卷（course_id 为 NULL 的卷无法归属到已加入课程，一并隐藏）
            if (studentId != null) {
                sb.append(" AND ").append(tableAlias).append(".course_id IN " +
                        "(SELECT course_id FROM course_member WHERE student_id = #{studentId}) ");
            }
            if (keyword != null && !keyword.trim().isEmpty()) {
                sb.append(" AND ").append(tableAlias).append(".paper_name LIKE CONCAT('%', #{keyword}, '%') ");
            }
            if (courseId != null) {
                sb.append(" AND ").append(tableAlias).append(".course_id = #{courseId} ");
            }
            if (status != null) {
                sb.append(" AND ").append(tableAlias).append(".status = #{status} ");
            }
            return sb.toString();
        }

        public String listPage(int offset, int pageSize, Long teacherId, Integer role,
                               String keyword, Long courseId, Integer status, Long studentId) {
            StringBuilder sb = new StringBuilder(
                    "SELECT pp.*, c.course_name AS courseName, " +
                    "(SELECT COUNT(*) FROM practice_record r WHERE r.practice_paper_id = pp.id) AS hasPracticeRecords, " +
                    "(SELECT COUNT(*) FROM practice_record r WHERE r.practice_paper_id = pp.id) AS practiceCount, " +
                    "(SELECT COUNT(DISTINCT r.student_id) FROM practice_record r WHERE r.practice_paper_id = pp.id) AS practiceUserCount");
            if (studentId != null) {
                sb.append(", (SELECT COUNT(*) FROM practice_record r WHERE r.practice_paper_id = pp.id AND r.student_id = ")
                  .append(studentId).append(") AS myPracticeCount");
                // 文件型作业：我对该作业最新一条「附件提交」记录ID，Controller 再批量回填提交详情
                sb.append(", (SELECT r.id FROM practice_record r WHERE r.practice_paper_id = pp.id AND r.student_id = ")
                  .append(studentId)
                  .append(" AND r.attachment_path IS NOT NULL ORDER BY r.id DESC LIMIT 1) AS mySubmitId");
            } else {
                sb.append(", 0 AS myPracticeCount, NULL AS mySubmitId");
            }
            sb.append(" FROM practice_paper pp ");
            sb.append("LEFT JOIN sys_course c ON pp.course_id = c.id ");
            sb.append(baseWhere("pp", teacherId, role, keyword, courseId, status, studentId));
            sb.append(" ORDER BY pp.create_time DESC LIMIT #{offset}, #{pageSize}");
            return sb.toString();
        }

        public String countPage(Long teacherId, Integer role,
                                String keyword, Long courseId, Integer status,
                                Long studentId) {
            StringBuilder sb = new StringBuilder("SELECT COUNT(*) FROM practice_paper pp ");
            sb.append(baseWhere("pp", teacherId, role, keyword, courseId, status, studentId));
            return sb.toString();
        }
    }
}
