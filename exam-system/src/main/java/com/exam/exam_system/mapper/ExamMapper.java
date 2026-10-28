package com.exam.exam_system.mapper;

import com.exam.exam_system.dto.ExamQuery;
import com.exam.exam_system.entity.Exam;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 考试 Mapper，对应 exam 表
 *
 * <p>列表联 exam_paper 带出试卷名称和题数；
 * 运行状态筛选在 SQL 里按时间窗口推导。
 */
@Mapper
public interface ExamMapper {

    @SelectProvider(type = ExamMapper.ExamSqlProvider.class, method = "listByQuery")
    List<Exam> listByQuery(@Param("q") ExamQuery q);

    @SelectProvider(type = ExamMapper.ExamSqlProvider.class, method = "countByQuery")
    long countByQuery(@Param("q") ExamQuery q);

    @Select("SELECT e.*, p.paper_name, c.course_name AS course_name, " +
            "(SELECT COUNT(*) FROM paper_question pq WHERE pq.paper_id = p.id) AS question_count " +
            "FROM exam e LEFT JOIN exam_paper p ON e.paper_id = p.id " +
            "LEFT JOIN sys_course c ON e.course_id = c.id " +
            "WHERE e.id = #{id}")
    Exam selectById(@Param("id") Long id);

    @Insert("INSERT INTO exam(exam_name, paper_id, course_id, start_time, end_time, duration, " +
            "total_score, pass_score, status, creator_id) " +
            "VALUES(#{examName}, #{paperId}, #{courseId}, #{startTime}, #{endTime}, #{duration}, " +
            "#{totalScore}, #{passScore}, #{status}, #{creatorId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Exam e);

    @Update("UPDATE exam SET exam_name=#{examName}, paper_id=#{paperId}, course_id=#{courseId}, " +
            "start_time=#{startTime}, end_time=#{endTime}, duration=#{duration}, " +
            "total_score=#{totalScore}, pass_score=#{passScore} WHERE id=#{id}")
    int updateById(Exam e);

    /** 发布 / 撤回 */
    @Update("UPDATE exam SET status=#{status} WHERE id=#{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 提前结束：把结束时间改为指定时刻（一般为 NOW()），学生端立即视为考试结束 */
    @Update("UPDATE exam SET end_time=#{endTime} WHERE id=#{id}")
    int updateEndTime(@Param("id") Long id, @Param("endTime") java.time.LocalDateTime endTime);

    /**
     * 动态更新考试时间窗口（重新发布用）：传哪个字段更新哪个，null 跳过
     * days 模式：只更新 endTime；自定义窗口模式：startTime + endTime 一起更新
     */
    @Update("<script>" +
            "UPDATE exam" +
            "<set>" +
            "<if test='startTime != null'>start_time = #{startTime}, </if>" +
            "<if test='endTime != null'>end_time = #{endTime}, </if>" +
            "</set>" +
            "WHERE id = #{id}" +
            "</script>")
    int updateSchedule(@Param("id") Long id,
                       @Param("startTime") java.time.LocalDateTime startTime,
                       @Param("endTime") java.time.LocalDateTime endTime);

    @Delete("DELETE FROM exam WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /** 总数（仪表盘考试数） */
    @Select("SELECT COUNT(*) FROM exam")
    long countAll();

    /** 学生端：全部已发布的考试（含未开始/进行中/已结束，运行状态由 Service 推导） */
    @Select("SELECT e.*, p.paper_name, c.course_name AS course_name, " +
            "(SELECT COUNT(*) FROM paper_question pq WHERE pq.paper_id = p.id) AS question_count " +
            "FROM exam e LEFT JOIN exam_paper p ON e.paper_id = p.id " +
            "LEFT JOIN sys_course c ON e.course_id = c.id " +
            "WHERE e.status = 1 ORDER BY e.start_time DESC")
    List<Exam> listPublished();

    /** 统计某试卷正被"进行中"考试使用的场次（已发布且当前时间在考试窗口内） */
    @Select("SELECT COUNT(*) FROM exam WHERE status = 1 AND paper_id = #{paperId} " +
            "AND start_time <= NOW() AND end_time >= NOW()")
    int countRunningByPaperId(@Param("paperId") Long paperId);

    /**
     * 按课程范围统计考试总数（教师端首页"我的考试"数字）。
     *
     * @param courseIds 教师任教课程集合；null = 不限制（管理员，等同全表统计）
     */
    @Select("<script>SELECT COUNT(*) FROM exam " +
            "<if test='courseIds != null'>WHERE course_id IN " +
            "<foreach collection='courseIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> " +
            "</if></script>")
    long countByCourses(@Param("courseIds") List<Long> courseIds);

    /** 批量查询正被"进行中"考试使用的试卷名称（用于删除前拦截提示） */
    @Select("<script>" +
            "SELECT DISTINCT p.paper_name FROM exam e JOIN exam_paper p ON e.paper_id = p.id " +
            "WHERE e.status = 1 AND e.start_time &lt;= NOW() AND e.end_time &gt;= NOW() " +
            "AND e.paper_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<String> listRunningPaperNames(@Param("ids") List<Long> ids);

    /** 内部类：动态 SQL 生成器 */
    class ExamSqlProvider {

        /** 运行状态筛选片段（0未开始 1进行中 2已结束 3已撤回） */
        private void appendRuntimeStatus(StringBuilder sb, Integer rs) {
            if (rs == null) {
                return;
            }
            switch (rs) {
                case 0 -> sb.append("AND e.start_time > NOW() AND e.status = 1 ");
                case 1 -> sb.append("AND e.start_time <= NOW() AND e.end_time >= NOW() AND e.status = 1 ");
                case 2 -> sb.append("AND e.end_time < NOW() ");
                case 3 -> sb.append("AND e.status = 0 ");
                default -> { /* 未知值不加条件 */ }
            }
        }

        public String listByQuery(@Param("q") ExamQuery q) {
            StringBuilder sb = new StringBuilder(
                    "SELECT e.*, p.paper_name, c.course_name AS course_name, " +
                    "(SELECT COUNT(*) FROM paper_question pq WHERE pq.paper_id = p.id) AS question_count " +
                    "FROM exam e LEFT JOIN exam_paper p ON e.paper_id = p.id " +
                    "LEFT JOIN sys_course c ON e.course_id = c.id WHERE 1=1 ");
            if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
                sb.append("AND e.exam_name LIKE CONCAT('%',#{q.keyword},'%') ");
            }
            if (q.getCourseId() != null) {
                sb.append("AND e.course_id = #{q.courseId} ");
            }
            // 数据权限：教师只能看到自己任教课程下的考试（由 TeacherScope 注入，管理员为 null）
            if (q.getCourseIds() != null && !q.getCourseIds().isEmpty()) {
                sb.append("AND e.course_id IN (");
                for (int i = 0; i < q.getCourseIds().size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append("#{q.courseIds[").append(i).append("]}");
                }
                sb.append(") ");
            }
            appendRuntimeStatus(sb, q.getRuntimeStatus());
            sb.append("ORDER BY e.id DESC LIMIT #{q.pageSize} OFFSET #{q.offset}");
            return sb.toString();
        }

        public String countByQuery(@Param("q") ExamQuery q) {
            StringBuilder sb = new StringBuilder("SELECT COUNT(*) FROM exam e WHERE 1=1 ");
            if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
                sb.append("AND e.exam_name LIKE CONCAT('%',#{q.keyword},'%') ");
            }
            if (q.getCourseId() != null) {
                sb.append("AND e.course_id = #{q.courseId} ");
            }
            // 数据权限：教师只能看到自己任教课程下的考试（由 TeacherScope 注入，管理员为 null）
            if (q.getCourseIds() != null && !q.getCourseIds().isEmpty()) {
                sb.append("AND e.course_id IN (");
                for (int i = 0; i < q.getCourseIds().size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append("#{q.courseIds[").append(i).append("]}");
                }
                sb.append(") ");
            }
            appendRuntimeStatus(sb, q.getRuntimeStatus());
            return sb.toString();
        }
    }
}
