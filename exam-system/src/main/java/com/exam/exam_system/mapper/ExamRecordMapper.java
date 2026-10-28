package com.exam.exam_system.mapper;

import com.exam.exam_system.dto.GradeQuery;
import com.exam.exam_system.dto.GradeStats;
import com.exam.exam_system.entity.ExamRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 考试记录 Mapper（成绩管理 + 学生作答共用）
 */
public interface ExamRecordMapper {

    /**
     * exam_record 表字段（显式列出，禁止用 r.*）
     *
     * <p>原因：exam_record 里已有 paper_name / paper_total_score / pass_score 三个快照列，
     * 如果用 {@code r.*} 再追加 {@code COALESCE(r.paper_name, p.paper_name) AS paper_name}，
     * 结果集里会出现两个同名 paper_name：</p>
     * <ul>
     *   <li>派生表（如成绩列表套 RANK() 窗口函数的外层）→ MySQL 直接报
     *       {@code Duplicate column name 'paper_name'}；</li>
     *   <li>顶层查询虽不报错，但 JDBC 按列名取值只拿到第一列（即快照 r.paper_name），
     *       COALESCE 兜底形同虚设。</li>
     * </ul>
     * <p>故统一改为显式列 + 唯一别名，快照兜底才真正生效。</p>
     */
    String RECORD_COLUMNS =
            "r.id, r.paper_id, r.exam_id, r.user_id, r.start_time, r.submit_time, " +
            "r.total_score, r.status, r.is_pass, r.create_time, ";

    /**
     * 试卷信息：优先读交卷时写入的成绩快照，快照为空（老数据）时联 exam_paper 兜底
     */
    String PAPER_COLUMNS =
            "COALESCE(r.paper_name, p.paper_name) AS paper_name, " +
            "COALESCE(r.paper_total_score, p.total_score) AS paper_total_score, " +
            "COALESCE(r.pass_score, p.pass_score) AS pass_score ";

    /**
     * 插入一条考试记录（学生交卷时），回填自增 id
     */
    @Insert("INSERT INTO exam_record(paper_id, exam_id, user_id, start_time, submit_time, total_score, status, is_pass, " +
            "paper_name, paper_total_score, pass_score) " +
            "VALUES(#{paperId}, #{examId}, #{userId}, #{startTime}, #{submitTime}, #{totalScore}, #{status}, #{isPass}, " +
            "#{paperName}, #{paperTotalScore}, #{passScore})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ExamRecord r);

    /**
     * 查某个学生对某张试卷【已交卷/已判分】的考试记录
     * （防重复交卷 + 我的考试列表标记已参加；status=0 的"考试中"记录不算）
     */
    @Select("SELECT * FROM exam_record " +
            "WHERE paper_id = #{paperId} AND user_id = #{userId} AND status >= 1 " +
            "ORDER BY id DESC LIMIT 1")
    ExamRecord findByPaperAndUser(@Param("paperId") Long paperId, @Param("userId") Long userId);

    /**
     * 查某个学生对某张试卷【进行中(status=0)】的考试记录
     * （学生首次进入考试时创建，用于记录真实开考时间与答题进度）
     */
    @Select("SELECT * FROM exam_record " +
            "WHERE paper_id = #{paperId} AND user_id = #{userId} AND status = 0 " +
            "ORDER BY id DESC LIMIT 1")
    ExamRecord findOngoingByPaperAndUser(@Param("paperId") Long paperId, @Param("userId") Long userId);

    /**
     * 交卷时更新考试记录（保留首次进入考试时的 start_time，用于时长校验）
     */
    @Update("UPDATE exam_record SET submit_time = #{submitTime}, total_score = #{totalScore}, " +
            "status = #{status}, is_pass = #{isPass}, " +
            "paper_name = #{paperName}, paper_total_score = #{paperTotalScore}, pass_score = #{passScore} " +
            "WHERE id = #{id}")
    int updateSubmit(ExamRecord r);

    /** 按 ID 查单条考试记录（阅卷用，联带学生姓名/学号/试卷名/学科名） */
    @Select("SELECT " + RECORD_COLUMNS + PAPER_COLUMNS + ", u.username, u.real_name, " +
            "c.course_name AS course_name " +
            "FROM exam_record r " +
            "LEFT JOIN sys_user u ON r.user_id = u.id " +
            "LEFT JOIN exam_paper p ON r.paper_id = p.id " +
            "LEFT JOIN sys_course c ON p.course_id = c.id " +
            "WHERE r.id = #{id}")
    ExamRecord selectById(@Param("id") Long id);

    /**
     * 分页查询待阅卷记录（status=1 已交卷待判分），联学生信息 + 试卷信息
     */
    @SelectProvider(type = SqlProvider.class, method = "listPending")
    List<ExamRecord> listPending(@Param("q") GradeQuery q);

    @SelectProvider(type = SqlProvider.class, method = "countPending")
    Long countPending(@Param("q") GradeQuery q);

    /**
     * 教师判分完成后更新考试记录：写总分、置为已判分、算是否及格
     */
    @Update("UPDATE exam_record SET total_score = #{totalScore}, status = #{status}, " +
            "is_pass = #{isPass} WHERE id = #{id}")
    int updateReviewScore(ExamRecord r);

    /**
     * 学生端：我的成绩列表（联试卷表带出试卷名和满分）
     *
     * <p>额外算出 can_view_paper：考试结束后（end_time 已过）才允许回看试卷，
     * 决定成绩列表里「查看试卷」按钮是否可点。口径与 Service 层的硬校验保持一致。</p>
     */
    @Select("SELECT " + RECORD_COLUMNS + PAPER_COLUMNS +
            ", CASE WHEN e.end_time IS NULL OR e.end_time < NOW() THEN 1 ELSE 0 END AS can_view_paper " +
            "FROM exam_record r LEFT JOIN exam_paper p ON r.paper_id = p.id " +
            "LEFT JOIN exam e ON r.exam_id = e.id " +
            "WHERE r.user_id = #{userId} AND r.status >= 1 " +
            "ORDER BY r.submit_time DESC")
    List<ExamRecord> listByUser(@Param("userId") Long userId);

    /**
     * 学生端：同卷里分数比我高的人数（我的名次 = 该值 + 1，同分并列）
     *
     * <p>只统计已交卷记录（status>=1），与成绩列表 rank 的口径保持一致。</p>
     */
    @Select("SELECT COUNT(*) FROM exam_record " +
            "WHERE paper_id = #{paperId} AND status >= 1 AND total_score > #{totalScore}")
    int countHigherScore(@Param("paperId") Long paperId, @Param("totalScore") BigDecimal totalScore);

    /**
     * 分页查询成绩：联 sys_user（学号/姓名）+ exam_paper（试卷名/满分）
     */
    @SelectProvider(type = SqlProvider.class, method = "listByQuery")
    List<ExamRecord> listByQuery(@Param("q") GradeQuery q);

    @SelectProvider(type = SqlProvider.class, method = "countByQuery")
    Long countByQuery(@Param("q") GradeQuery q);

    /**
     * 成绩统计：人数 / 平均 / 最高 / 最低 / 及格数 / 分数段分布
     * 分数段按得分率（total_score / 试卷满分 × 100）划分，跨试卷统一口径
     */
    @Select("<script>" +
            "SELECT COUNT(*) AS totalCount, " +
            "  IFNULL(ROUND(AVG(r.total_score), 1), 0) AS avgScore, " +
            "  IFNULL(MAX(r.total_score), 0) AS maxScore, " +
            "  IFNULL(MIN(r.total_score), 0) AS minScore, " +
            "  IFNULL(SUM(r.is_pass), 0) AS passCount, " +
            "  SUM(CASE WHEN r.total_score * 100.0 / NULLIF(p.total_score, 0) &lt; 60 THEN 1 ELSE 0 END) AS d0To59, " +
            "  SUM(CASE WHEN r.total_score * 100.0 / NULLIF(p.total_score, 0) &gt;= 60 " +
            "        AND r.total_score * 100.0 / NULLIF(p.total_score, 0) &lt; 70 THEN 1 ELSE 0 END) AS d60To69, " +
            "  SUM(CASE WHEN r.total_score * 100.0 / NULLIF(p.total_score, 0) &gt;= 70 " +
            "        AND r.total_score * 100.0 / NULLIF(p.total_score, 0) &lt; 80 THEN 1 ELSE 0 END) AS d70To79, " +
            "  SUM(CASE WHEN r.total_score * 100.0 / NULLIF(p.total_score, 0) &gt;= 80 " +
            "        AND r.total_score * 100.0 / NULLIF(p.total_score, 0) &lt; 90 THEN 1 ELSE 0 END) AS d80To89, " +
            "  SUM(CASE WHEN r.total_score * 100.0 / NULLIF(p.total_score, 0) &gt;= 90 THEN 1 ELSE 0 END) AS d90To100 " +
            "FROM exam_record r " +
            "LEFT JOIN exam_paper p ON r.paper_id = p.id " +
            "WHERE r.status &gt;= 1 " +
            "<if test='paperId != null'> AND r.paper_id = #{paperId} </if>" +
            "<if test='courseId != null'> AND p.course_id = #{courseId} </if>" +
            "<if test='courseIds != null'> AND p.course_id IN " +
            "<foreach collection='courseIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> </if>" +
            "</script>")
    GradeStats stats(@Param("paperId") Long paperId, @Param("courseId") Long courseId,
                     @Param("courseIds") List<Long> courseIds);

    /**
     * 按试卷 ID 查询所有考试记录（导出用）
     */
    @Select("SELECT * FROM exam_record WHERE paper_id = #{paperId} AND status >= 1 ORDER BY total_score DESC")
    List<ExamRecord> listByPaperId(@Param("paperId") Long paperId);

    /**
     * 删除某场考试的全部成绩记录（删除考试时级联清理）。
     * exam_id 为 NULL 的历史记录无法归属到具体考试，保持不动。
     */
    @Delete("DELETE FROM exam_record WHERE exam_id = #{examId}")
    int deleteByExamId(@Param("examId") Long examId);

    /**
     * 动态 SQL 提供者
     */
    class SqlProvider {

        /**
         * 待阅卷列表：status=1（已交卷、简答题待教师判分），按交卷时间倒序
         */
        public String listPending(@Param("q") GradeQuery q) {
            StringBuilder sb = new StringBuilder(
                    "SELECT " + RECORD_COLUMNS + PAPER_COLUMNS + ", u.username, u.real_name, " +
                    "c.course_name AS course_name " +
                    "FROM exam_record r " +
                    "LEFT JOIN sys_user u ON r.user_id = u.id " +
                    "LEFT JOIN exam_paper p ON r.paper_id = p.id " +
                    "LEFT JOIN sys_course c ON p.course_id = c.id " +
                    "WHERE r.status = 1 ");
            if (q.getPaperId() != null) {
                sb.append("AND r.paper_id = #{q.paperId} ");
            }
            if (q.getCourseId() != null) {
                sb.append("AND p.course_id = #{q.courseId} ");
            }
            // 数据权限：教师只能看到自己任教课程下的成绩（由 TeacherScope 注入，管理员为 null）
            if (q.getCourseIds() != null && !q.getCourseIds().isEmpty()) {
                sb.append("AND p.course_id IN (");
                for (int i = 0; i < q.getCourseIds().size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append("#{q.courseIds[").append(i).append("]}");
                }
                sb.append(") ");
            }
            if (q.getKeyword() != null && !q.getKeyword().trim().isEmpty()) {
                sb.append("AND (u.username LIKE CONCAT('%', #{q.keyword}, '%') " +
                          "OR u.real_name LIKE CONCAT('%', #{q.keyword}, '%')) ");
            }
            sb.append("ORDER BY r.submit_time DESC LIMIT #{q.offset}, #{q.pageSize}");
            return sb.toString();
        }

        public String countPending(@Param("q") GradeQuery q) {
            StringBuilder sb = new StringBuilder(
                    "SELECT COUNT(*) FROM exam_record r " +
                    "LEFT JOIN sys_user u ON r.user_id = u.id " +
                    "LEFT JOIN exam_paper p ON r.paper_id = p.id " +
                    "LEFT JOIN sys_course c ON p.course_id = c.id " +
                    "WHERE r.status = 1 ");
            if (q.getPaperId() != null) {
                sb.append("AND r.paper_id = #{q.paperId} ");
            }
            if (q.getCourseId() != null) {
                sb.append("AND p.course_id = #{q.courseId} ");
            }
            // 数据权限：教师只能看到自己任教课程下的成绩（由 TeacherScope 注入，管理员为 null）
            if (q.getCourseIds() != null && !q.getCourseIds().isEmpty()) {
                sb.append("AND p.course_id IN (");
                for (int i = 0; i < q.getCourseIds().size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append("#{q.courseIds[").append(i).append("]}");
                }
                sb.append(") ");
            }
            if (q.getKeyword() != null && !q.getKeyword().trim().isEmpty()) {
                sb.append("AND (u.username LIKE CONCAT('%', #{q.keyword}, '%') " +
                          "OR u.real_name LIKE CONCAT('%', #{q.keyword}, '%')) ");
            }
            return sb.toString();
        }

        public String listByQuery(@Param("q") GradeQuery q) {
            // 外层套窗口函数：按 paper_id 分组、同卷内按 total_score 倒序排名（rank 字段）
            // 排序保持原行为（按交卷时间倒序），rank 是行属性、不受展示顺序影响
            StringBuilder sb = new StringBuilder(
                    "SELECT t.*, RANK() OVER (PARTITION BY t.paper_id ORDER BY t.total_score DESC) AS `rank` " +
                    "FROM ( " +
                    "SELECT " + RECORD_COLUMNS + PAPER_COLUMNS + ", u.username, u.real_name, " +
                    "c.course_name AS course_name " +
                    "FROM exam_record r " +
                    "LEFT JOIN sys_user u ON r.user_id = u.id " +
                    "LEFT JOIN exam_paper p ON r.paper_id = p.id " +
                    "LEFT JOIN sys_course c ON p.course_id = c.id " +
                    "WHERE r.status >= 1 ");
            if (q.getPaperId() != null) {
                sb.append("AND r.paper_id = #{q.paperId} ");
            }
            if (q.getCourseId() != null) {
                sb.append("AND p.course_id = #{q.courseId} ");
            }
            // 数据权限：教师只能看到自己任教课程下的成绩（由 TeacherScope 注入，管理员为 null）
            if (q.getCourseIds() != null && !q.getCourseIds().isEmpty()) {
                sb.append("AND p.course_id IN (");
                for (int i = 0; i < q.getCourseIds().size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append("#{q.courseIds[").append(i).append("]}");
                }
                sb.append(") ");
            }
            if (q.getKeyword() != null && !q.getKeyword().trim().isEmpty()) {
                sb.append("AND (u.username LIKE CONCAT('%', #{q.keyword}, '%') " +
                          "OR u.real_name LIKE CONCAT('%', #{q.keyword}, '%')) ");
            }
            sb.append(") t ");
            // 默认按排名（rank 升序 = 分数降序）展示；同分按交卷时间倒序
            sb.append("ORDER BY `rank` ASC, t.submit_time DESC LIMIT #{q.offset}, #{q.pageSize}");
            return sb.toString();
        }

        public String countByQuery(@Param("q") GradeQuery q) {
            StringBuilder sb = new StringBuilder(
                    "SELECT COUNT(*) FROM exam_record r " +
                    "LEFT JOIN sys_user u ON r.user_id = u.id " +
                    "LEFT JOIN exam_paper p ON r.paper_id = p.id " +
                    "LEFT JOIN sys_course c ON p.course_id = c.id " +
                    "WHERE r.status >= 1 ");
            if (q.getPaperId() != null) {
                sb.append("AND r.paper_id = #{q.paperId} ");
            }
            if (q.getCourseId() != null) {
                sb.append("AND p.course_id = #{q.courseId} ");
            }
            // 数据权限：教师只能看到自己任教课程下的成绩（由 TeacherScope 注入，管理员为 null）
            if (q.getCourseIds() != null && !q.getCourseIds().isEmpty()) {
                sb.append("AND p.course_id IN (");
                for (int i = 0; i < q.getCourseIds().size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append("#{q.courseIds[").append(i).append("]}");
                }
                sb.append(") ");
            }
            if (q.getKeyword() != null && !q.getKeyword().trim().isEmpty()) {
                sb.append("AND (u.username LIKE CONCAT('%', #{q.keyword}, '%') " +
                          "OR u.real_name LIKE CONCAT('%', #{q.keyword}, '%')) ");
            }
            return sb.toString();
        }
    }
}
