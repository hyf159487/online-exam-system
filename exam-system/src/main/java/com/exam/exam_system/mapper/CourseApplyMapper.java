package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.CourseApply;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 学生加入课程申请 Mapper，对应 course_apply 表
 */
@Mapper
public interface CourseApplyMapper {

    @Insert("INSERT INTO course_apply (student_id, course_id, apply_type, status) " +
            "VALUES (#{studentId}, #{courseId}, #{applyType}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CourseApply apply);

    @Select("SELECT * FROM course_apply WHERE id = #{id} LIMIT 1")
    CourseApply selectById(@Param("id") Long id);

    /** 某学生某课程最新一条未删除的申请记录（判重 & 拿当前状态用） */
    @Select("SELECT * FROM course_apply " +
            "WHERE student_id = #{studentId} AND course_id = #{courseId} AND is_deleted = 0 " +
            "ORDER BY id DESC LIMIT 1")
    CourseApply selectLatest(@Param("studentId") Long studentId, @Param("courseId") Long courseId);

    /**
     * 待审核申请里，某课程是否存在指定类型的 pending（防止同一学生重复申请）
     * applyType：0加入 1退出（可空，空=不限类型）
     */
    @Select("<script>SELECT COUNT(*) FROM course_apply " +
            "WHERE student_id = #{studentId} AND course_id = #{courseId} AND status = 0 " +
            "AND is_deleted = 0 " +
            "<if test='applyType != null'> AND apply_type = #{applyType} </if>" +
            "</script>")
    int countPending(@Param("studentId") Long studentId, @Param("courseId") Long courseId,
                     @Param("applyType") Integer applyType);

    /**
     * 列表（联查学生/班级/课程/任课教师）
     * status 可选：null 返回全部（学生端"我的申请"用），0 返回待审核（教师端用）
     * studentId 可选：非空则只看该学生的申请记录
     */
    @SelectProvider(type = SqlProvider.class, method = "selectList")
    List<CourseApply> selectList(@Param("status") Integer status,
                                 @Param("studentId") Long studentId);

    /** 待审核申请总数（教师端角标/未读提示用） */
    @Select("SELECT COUNT(*) FROM course_apply WHERE status = 0 AND is_deleted = 0")
    long countPendingAll();

    /**
     * 教师端：仅返回该教师任教课程的申请列表
     * status 可选：null=该教师任教课程的全部申请，0=待审核
     *
     * <p>注意：必须用 @Select("<script>...") 包裹，<foreach> 才会被解析；
     * @SelectProvider 返回的普通字符串不会解析 XML 标签。
     */
    @Select("<script>SELECT ca.*, " +
            "u.real_name AS studentName, u.username AS studentUsername, " +
            "cls.class_name AS className, " +
            "c.course_name AS courseName, c.course_code AS courseCode, " +
            "c.description AS courseDescription, " +
            "(SELECT GROUP_CONCAT(t.real_name ORDER BY t.id SEPARATOR '、') " +
            "   FROM sys_course_teacher ct JOIN sys_user t ON t.id = ct.teacher_id " +
            "   WHERE ct.course_id = ca.course_id) AS teacherNames " +
            "FROM course_apply ca " +
            "JOIN sys_user u ON u.id = ca.student_id " +
            "LEFT JOIN sys_class cls ON cls.id = u.class_id " +
            "JOIN sys_course c ON c.id = ca.course_id " +
            "WHERE ca.is_deleted = 0 AND ca.course_id IN " +
            "<foreach collection='courseIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> " +
            "<if test='status != null'> AND ca.status = #{status} </if> " +
            "ORDER BY ca.apply_time DESC, ca.id DESC</script>")
    List<CourseApply> selectListByCourses(@Param("status") Integer status,
                                          @Param("courseIds") List<Long> courseIds);

    /** 教师端：该教师任教课程的待审核申请数 */
    @Select("<script>SELECT COUNT(*) FROM course_apply " +
            "WHERE status = 0 AND is_deleted = 0 AND course_id IN " +
            "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    long countPendingByCourses(@Param("courseIds") List<Long> courseIds);

    /**
     * 教师端：本月已处理（审核通过/驳回）的申请数，审核页统计卡片用
     * <p>按 audit_time 落在当前自然月判断，不用 create_time（申请可能是上个月提交、本月才审）
     */
    @Select("<script>SELECT COUNT(*) FROM course_apply " +
            "WHERE is_deleted = 0 AND status != 0 AND audit_time IS NOT NULL " +
            "AND DATE_FORMAT(audit_time, '%Y-%m') = DATE_FORMAT(NOW(), '%Y-%m') " +
            "AND course_id IN " +
            "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    long countHandledThisMonthByCourses(@Param("courseIds") List<Long> courseIds);

    /**
     * 教师端：申请学生基础信息（申请详情弹窗用）
     * <p>返回 Map，字段必须显式写别名（Map 结果不会走驼峰自动映射）
     */
    @Select("SELECT u.id, u.username, u.real_name AS realName, u.phone, u.email, u.status, " +
            "u.create_time AS registerTime, cls.class_name AS className " +
            "FROM sys_user u LEFT JOIN sys_class cls ON cls.id = u.class_id " +
            "WHERE u.id = #{studentId} LIMIT 1")
    Map<String, Object> selectStudentInfo(@Param("studentId") Long studentId);

    /** 某学生当前已加入的课程数（申请详情弹窗用） */
    @Select("SELECT COUNT(*) FROM course_member WHERE student_id = #{studentId}")
    int countJoinedCourses(@Param("studentId") Long studentId);

    /**
     * 教师端：某学生在本教师任教课程下的全部申请记录（详情弹窗下半部分）
     * <p>只看该教师任教课程，避免教师通过此接口打探非任教课程的申请情况
     */
    @Select("<script>SELECT ca.*, c.course_name AS courseName, c.course_code AS courseCode, " +
            "c.description AS courseDescription " +
            "FROM course_apply ca " +
            "JOIN sys_course c ON c.id = ca.course_id " +
            "WHERE ca.is_deleted = 0 AND ca.student_id = #{studentId} AND ca.course_id IN " +
            "<foreach collection='courseIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> " +
            "ORDER BY ca.id DESC</script>")
    List<CourseApply> selectByStudentInCourses(@Param("studentId") Long studentId,
                                               @Param("courseIds") List<Long> courseIds);

    /** 更新审核结果：status=1 通过 / 2 驳回，审计教师与时间 */
    @Update("UPDATE course_apply SET status = #{status}, reject_reason = #{rejectReason}, " +
            "audit_teacher_id = #{auditTeacherId}, audit_time = NOW() " +
            "WHERE id = #{id}")
    int updateAudit(@Param("id") Long id, @Param("status") Integer status,
                    @Param("rejectReason") String rejectReason,
                    @Param("auditTeacherId") Long auditTeacherId);

    class SqlProvider {
        public String selectList(Integer status, Long studentId) {
            StringBuilder sb = new StringBuilder(
                    "SELECT ca.*, " +
                    "u.real_name AS studentName, u.username AS studentUsername, " +
                    "cls.class_name AS className, " +
                    "c.course_name AS courseName, c.course_code AS courseCode, " +
                    "c.description AS courseDescription, " +
                    "(SELECT GROUP_CONCAT(t.real_name ORDER BY t.id SEPARATOR '、') " +
                    "   FROM sys_course_teacher ct JOIN sys_user t ON t.id = ct.teacher_id " +
                    "   WHERE ct.course_id = ca.course_id) AS teacherNames " +
                    "FROM course_apply ca " +
                    "JOIN sys_user u ON u.id = ca.student_id " +
                    "LEFT JOIN sys_class cls ON cls.id = u.class_id " +
                    "JOIN sys_course c ON c.id = ca.course_id " +
                    "WHERE ca.is_deleted = 0 ");
            if (status != null) {
                sb.append(" AND ca.status = #{status} ");
            }
            if (studentId != null) {
                sb.append(" AND ca.student_id = #{studentId} ");
            }
            sb.append(" ORDER BY ca.apply_time DESC, ca.id DESC ");
            return sb.toString();
        }
    }
}
