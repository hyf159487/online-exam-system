package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.Course;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 课程 Mapper，对应 sys_course 表
 */
@Mapper
public interface CourseMapper {

    /** 查全部课程（下拉框用），按 id 排序保证顺序稳定 */
    @Select("SELECT * FROM sys_course ORDER BY id")
    List<Course> selectAll();

    /**
     * 查全部课程 + 任课教师姓名聚合（管理端列表 / 学生端加入课程卡片用）
     * 一门课多名教师用逗号拼接；无教师为 NULL
     * 同时统计每门课已加入学生数（studentCount），学生端卡片展示「参加人数」
     */
    @Select("SELECT c.id, c.course_name AS courseName, c.course_code AS courseCode, " +
            "c.course_type AS courseType, c.description, c.cover, c.start_time AS startTime, " +
            "c.enroll_deadline AS enrollDeadline, c.create_time AS createTime, " +
            "(SELECT COUNT(*) FROM course_member cm WHERE cm.course_id = c.id) AS studentCount, " +
            "GROUP_CONCAT(t.real_name ORDER BY t.id SEPARATOR '、') AS teacherNames " +
            "FROM sys_course c " +
            "LEFT JOIN sys_course_teacher ct ON ct.course_id = c.id " +
            "LEFT JOIN sys_user t ON t.id = ct.teacher_id " +
            "GROUP BY c.id ORDER BY c.id")
    List<Course> selectAllWithTeachers();

    /**
     * 课程类型去重列表（学生端「课程类型」筛选下拉用，按类型名排序）
     */
    @Select("SELECT DISTINCT course_type FROM sys_course " +
            "WHERE course_type IS NOT NULL AND TRIM(course_type) != '' " +
            "ORDER BY course_type")
    List<String> selectDistinctCourseTypes();

    /**
     * 课程名称去重列表（学生端「课程名称」筛选下拉候选用，按名称排序）
     */
    @Select("SELECT DISTINCT course_name FROM sys_course " +
            "WHERE course_name IS NOT NULL AND TRIM(course_name) != '' " +
            "ORDER BY course_name")
    List<String> selectDistinctCourseNames();

    /**
     * 授课教师姓名去重列表（学生端「授课教师」筛选下拉候选用，按姓名排序）
     * 仅统计已绑定课程的教师（sys_course_teacher），避免出现未任课教师
     */
    @Select("SELECT DISTINCT u.real_name FROM sys_course_teacher ct " +
            "JOIN sys_user u ON u.id = ct.teacher_id " +
            "WHERE u.real_name IS NOT NULL AND TRIM(u.real_name) != '' " +
            "ORDER BY u.real_name")
    List<String> selectDistinctTeacherNames();

    /**
     * 关键字模糊查询课程（下拉远端搜索用，不含任课教师聚合）
     * keyword 为空时返回前 limit 条（按 id 排序），非空按课程名称/编码 LIKE 匹配
     */
    @Select("<script>SELECT c.id, c.course_name AS courseName, c.course_code AS courseCode, " +
            "c.course_type AS courseType, c.description, c.create_time AS createTime " +
            "FROM sys_course c " +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (c.course_name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR c.course_code LIKE CONCAT('%', #{keyword}, '%'))" +
            "</if>" +
            "</where> ORDER BY c.id LIMIT #{limit}</script>")
    List<Course> selectByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    /**
     * 按 id 查课程（含任课教师姓名聚合 + 详情页扩展字段）
     * <p>intro / learningObjectives / targetAudience / syllabus 供学生端课程详情页「课程介绍」Tab 使用
     */
    @Select("SELECT c.id, c.course_name AS courseName, c.course_code AS courseCode, " +
            "c.course_type AS courseType, c.description, c.intro, " +
            "c.learning_objectives AS learningObjectives, c.target_audience AS targetAudience, " +
            "c.syllabus, c.cover, c.start_time AS startTime, " +
            "c.enroll_deadline AS enrollDeadline, c.create_time AS createTime, " +
            "GROUP_CONCAT(t.real_name ORDER BY t.id SEPARATOR '、') AS teacherNames " +
            "FROM sys_course c " +
            "LEFT JOIN sys_course_teacher ct ON ct.course_id = c.id " +
            "LEFT JOIN sys_user t ON t.id = ct.teacher_id " +
            "WHERE c.id = #{id} GROUP BY c.id LIMIT 1")
    Course selectByIdWithTeachers(@Param("id") Long id);

    /** 按 id 查课程（编辑回填基础信息用） */
    @Select("SELECT * FROM sys_course WHERE id = #{id} LIMIT 1")
    Course selectById(@Param("id") Long id);

    /** 查课程绑定的教师ID列表（编辑回选用） */
    @Select("SELECT teacher_id FROM sys_course_teacher WHERE course_id = #{courseId}")
    List<Long> selectTeacherIds(@Param("courseId") Long courseId);

    /** 查全部启用教师ID（一键分配任课教师时的教师池，role=1 且 status=1） */
    @Select("SELECT id FROM sys_user WHERE role = 1 AND status = 1 ORDER BY id")
    List<Long> selectAllTeacherIds();

    /** 查某教师任教的课程ID列表（课程加入申请按任教教师过滤用） */
    @Select("SELECT course_id FROM sys_course_teacher WHERE teacher_id = #{teacherId}")
    List<Long> selectCourseIdsByTeacher(@Param("teacherId") Long teacherId);

    /**
     * 教师端「我的课程」：仅返回该教师任教的课程，并统计每门课已加入学生数
     * keyword 非空时按课程名称/编码模糊过滤
     */
    @Select("<script>SELECT c.id, c.course_name AS courseName, c.course_code AS courseCode, " +
            "c.course_type AS courseType, c.description, c.cover, c.start_time AS startTime, " +
            "c.enroll_deadline AS enrollDeadline, c.create_time AS createTime, " +
            "(SELECT COUNT(*) FROM course_member cm WHERE cm.course_id = c.id) AS studentCount " +
            "FROM sys_course c " +
            "JOIN sys_course_teacher ct ON ct.course_id = c.id AND ct.teacher_id = #{teacherId} " +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (c.course_name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR c.course_code LIKE CONCAT('%', #{keyword}, '%'))" +
            "</if>" +
            "<if test='courseType != null and courseType != \"\"'>" +
            "AND c.course_type LIKE CONCAT('%', #{courseType}, '%')" +
            "</if>" +
            "</where> ORDER BY c.id</script>")
    List<java.util.Map<String, Object>> selectMyCourses(@Param("teacherId") Long teacherId,
                                                        @Param("keyword") String keyword,
                                                        @Param("courseType") String courseType);

    /** 查课程绑定的教师详情列表（详情页展示用） */
    @Select("SELECT u.id, u.username, u.real_name AS realName, u.phone, u.email " +
            "FROM sys_course_teacher ct JOIN sys_user u ON u.id = ct.teacher_id " +
            "WHERE ct.course_id = #{courseId} ORDER BY u.id")
    List<java.util.Map<String, Object>> selectTeachers(@Param("courseId") Long courseId);

    /** 删除课程的全部教师绑定（重新绑定前清空用） */
    @Delete("DELETE FROM sys_course_teacher WHERE course_id = #{courseId}")
    int deleteTeachers(@Param("courseId") Long courseId);

    /** 新增一条课程-教师绑定 */
    @Insert("INSERT INTO sys_course_teacher (course_id, teacher_id) VALUES (#{courseId}, #{teacherId})")
    int insertTeacher(@Param("courseId") Long courseId, @Param("teacherId") Long teacherId);

    /** 新增课程（含详情页扩展字段：详细介绍 / 学习目标 / 适用人群 / 课程大纲） */
    @Insert("INSERT INTO sys_course (course_name, course_code, course_type, description, intro, " +
            "learning_objectives, target_audience, syllabus, cover, start_time, enroll_deadline) " +
            "VALUES (#{courseName}, #{courseCode}, #{courseType}, #{description}, #{intro}, " +
            "#{learningObjectives}, #{targetAudience}, #{syllabus}, #{cover}, #{startTime}, #{enrollDeadline})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Course course);

    /** 更新课程信息（含详情页扩展字段） */
    @Update("UPDATE sys_course SET course_name = #{courseName}, course_code = #{courseCode}, " +
            "course_type = #{courseType}, description = #{description}, intro = #{intro}, " +
            "learning_objectives = #{learningObjectives}, target_audience = #{targetAudience}, " +
            "syllabus = #{syllabus}, cover = #{cover}, " +
            "start_time = #{startTime}, enroll_deadline = #{enrollDeadline} WHERE id = #{id}")
    int update(Course course);

    /**
     * 教师维护本人任教课程的信息（教师端「我的课程 → 课程信息」用）
     *
     * <p>与 {@link #update(Course)} 的差别：<b>不修改 course_code 与 sys_course_teacher 绑定</b>，
     * 课程编码是题库/试卷/考试的关联依据，任课教师由管理员统一维护，教师端不提供修改入口。
     */
    @Update("UPDATE sys_course SET course_name = #{courseName}, course_type = #{courseType}, " +
            "description = #{description}, intro = #{intro}, " +
            "learning_objectives = #{learningObjectives}, target_audience = #{targetAudience}, " +
            "syllabus = #{syllabus}, cover = #{cover}, " +
            "start_time = #{startTime}, enroll_deadline = #{enrollDeadline} WHERE id = #{id}")
    int updateByTeacher(Course course);

    /** 导入时仅补充数据库中为空的课程字段，不覆盖已有内容 */
    @Update("<script>UPDATE sys_course SET " +
            "course_code = CASE WHEN (course_code IS NULL OR TRIM(course_code) = '') AND #{courseCode} IS NOT NULL AND TRIM(#{courseCode}) != '' THEN #{courseCode} ELSE course_code END, " +
            "course_type = CASE WHEN (course_type IS NULL OR TRIM(course_type) = '') AND #{courseType} IS NOT NULL AND TRIM(#{courseType}) != '' THEN #{courseType} ELSE course_type END, " +
            "description = CASE WHEN (description IS NULL OR TRIM(description) = '') AND #{description} IS NOT NULL AND TRIM(#{description}) != '' THEN #{description} ELSE description END " +
            "WHERE id = #{id}</script>")
    int fillEmptyFields(Course course);

    /** 删除课程 */
    @Delete("DELETE FROM sys_course WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /** 按课程名查课程（新增/更新时查重用） */
    @Select("SELECT * FROM sys_course WHERE course_name = #{name} LIMIT 1")
    Course selectByName(@Param("name") String name);

    /** 按课程编码查课程（编码唯一校验用） */
    @Select("SELECT * FROM sys_course WHERE course_code = #{code} LIMIT 1")
    Course selectByCode(@Param("code") String code);

    /** 统计课程被题目引用的数量（删除校验用） */
    @Select("SELECT COUNT(*) FROM question WHERE course_id = #{id}")
    long countQuestionRef(@Param("id") Long id);

    /** 统计课程被试卷引用的数量（删除校验用） */
    @Select("SELECT COUNT(*) FROM exam_paper WHERE course_id = #{id}")
    long countPaperRef(@Param("id") Long id);

    /** 统计课程被考试引用的数量（删除校验用） */
    @Select("SELECT COUNT(*) FROM exam WHERE course_id = #{id}")
    long countExamRef(@Param("id") Long id);

    /** 统计课程被知识点引用的数量（删除校验用） */
    @Select("SELECT COUNT(*) FROM knowledge_point WHERE course_id = #{id}")
    long countKnowledgeRef(@Param("id") Long id);

    /** 删除课程下的全部知识点（强制删除课程前清理用） */
    @Delete("DELETE FROM knowledge_point WHERE course_id = #{courseId}")
    int deleteKnowledgeByCourseId(@Param("courseId") Long courseId);

    /** 按 id 批量查课程（删除前生成友好报告名用） */
    @Select("<script>SELECT * FROM sys_course WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<Course> selectByIds(@Param("ids") List<Long> ids);
}
