package com.exam.exam_system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 学生已加入课程表 Mapper，对应 course_member 表
 */
@Mapper
public interface CourseMemberMapper {

    /** 学生已加入的课程 ID 列表 */
    @Select("SELECT course_id FROM course_member WHERE student_id = #{studentId}")
    List<Long> selectJoinedCourseIds(@Param("studentId") Long studentId);

    /** 某学生是否已加入某课程 */
    @Select("SELECT COUNT(*) FROM course_member WHERE student_id = #{studentId} AND course_id = #{courseId}")
    int countJoined(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    /** 添加学生-课程关联（审核通过时写入） */
    @Insert("INSERT INTO course_member (student_id, course_id) VALUES (#{studentId}, #{courseId})")
    int insert(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    /** 移除学生-课程关联（退课审核通过 / 教师移除学员时用） */
    @Delete("DELETE FROM course_member WHERE student_id = #{studentId} AND course_id = #{courseId}")
    int delete(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    /** 某课程已加入学生数（教师端「我的课程」列表统计用） */
    @Select("SELECT COUNT(*) FROM course_member WHERE course_id = #{courseId}")
    long countByCourse(@Param("courseId") Long courseId);

    /** 某课程已加入学生ID列表（课程公告发布后推送系统通知用） */
    @Select("SELECT student_id FROM course_member WHERE course_id = #{courseId}")
    List<Long> selectStudentIds(@Param("courseId") Long courseId);

    /**
     * 某课程已加入学生名单（教师端「查看学生」用）
     * keyword 非空时按姓名/学号（用户名）模糊过滤
     */
    @Select("<script>SELECT u.id AS studentId, u.username, u.real_name AS realName, " +
            "u.class_id AS classId, cls.class_name AS className, u.phone, u.email, " +
            "cm.join_time AS joinTime " +
            "FROM course_member cm " +
            "JOIN sys_user u ON u.id = cm.student_id " +
            "LEFT JOIN sys_class cls ON cls.id = u.class_id " +
            "WHERE cm.course_id = #{courseId} " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (u.real_name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR u.username LIKE CONCAT('%', #{keyword}, '%'))" +
            "</if>" +
            " ORDER BY u.class_id, u.id</script>")
    List<java.util.Map<String, Object>> selectStudentsByCourse(@Param("courseId") Long courseId,
                                                               @Param("keyword") String keyword);

    /**
     * 学生端「个人中心 → 我的课程」：我已加入的课程信息
     * <p>一门课多名教师用「、」拼接（teacherNames）；studentCount 为课程总人数；
     * resourceCount 为课程学习资料数；joinTime 为本人加入时间；
     * quitPending 为「是否存在待审核的退出申请」（1 表示审核中，页面据此提示并禁止重复提交）。
     * <p>keyword 非空时按 课程名称 / 课程编码 / 任课教师姓名 模糊过滤。
     */
    @Select("<script>SELECT c.id, c.course_name AS courseName, c.course_code AS courseCode, " +
            "c.course_type AS courseType, c.description, c.cover, c.start_time AS startTime, " +
            "c.enroll_deadline AS enrollDeadline, c.create_time AS createTime, " +
            "cm.join_time AS joinTime, " +
            "(SELECT COUNT(*) FROM course_member m WHERE m.course_id = c.id) AS studentCount, " +
            "(SELECT COUNT(*) FROM study_resource sr WHERE sr.course_id = c.id AND sr.is_deleted = 0) AS resourceCount, " +
            "(SELECT COUNT(*) FROM course_apply ca WHERE ca.student_id = cm.student_id " +
            "   AND ca.course_id = c.id AND ca.apply_type = 1 AND ca.status = 0 AND ca.is_deleted = 0) AS quitPending, " +
            "(SELECT GROUP_CONCAT(t.real_name ORDER BY t.id SEPARATOR '、') " +
            "   FROM sys_course_teacher ct JOIN sys_user t ON t.id = ct.teacher_id " +
            "   WHERE ct.course_id = c.id) AS teacherNames " +
            "FROM course_member cm " +
            "JOIN sys_course c ON c.id = cm.course_id " +
            "WHERE cm.student_id = #{studentId} " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (c.course_name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR c.course_code LIKE CONCAT('%', #{keyword}, '%') " +
            "OR EXISTS (SELECT 1 FROM sys_course_teacher ct2 JOIN sys_user u2 ON u2.id = ct2.teacher_id " +
            "           WHERE ct2.course_id = c.id AND u2.real_name LIKE CONCAT('%', #{keyword}, '%'))) " +
            "</if>" +
            "ORDER BY cm.join_time DESC, c.id DESC</script>")
    List<java.util.Map<String, Object>> selectMyJoinedCourses(@Param("studentId") Long studentId,
                                                              @Param("keyword") String keyword);
}
