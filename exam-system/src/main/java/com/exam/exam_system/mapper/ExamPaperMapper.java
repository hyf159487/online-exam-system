package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.ExamPaper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 试卷 Mapper，对应 exam_paper 表
 *
 * <p>带可选条件的分页查询用 @SelectProvider 动态拼 SQL，简单查询用注解
 */
@Mapper
public interface ExamPaperMapper {

    /**
     * 最近 N 张（按创建时间倒序，过滤逻辑删除）
     *
     * @param courseIds 数据权限：非空时只查这些课程下的试卷（教师任教课程集合）；
     *                  null = 不限制（管理员）
     */
    @Select("<script>SELECT * FROM exam_paper WHERE is_deleted = 0 " +
            "<if test='courseIds != null'>AND course_id IN " +
            "<foreach collection='courseIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> " +
            "</if>" +
            "ORDER BY id DESC LIMIT #{limit}</script>")
    List<ExamPaper> listRecent(@Param("limit") int limit, @Param("courseIds") List<Long> courseIds);

    /** 按 ID 查单个（不过滤逻辑删除，便于权限校验时识别已删除试卷） */
    @Select("SELECT * FROM exam_paper WHERE id = #{id}")
    ExamPaper selectById(@Param("id") Long id);

    /** 按创建人查（教师查看"我创建的试卷"，过滤逻辑删除） */
    @Select("SELECT * FROM exam_paper WHERE creator_id = #{creatorId} AND is_deleted = 0 ORDER BY id DESC")
    List<ExamPaper> listByCreator(@Param("creatorId") Long creatorId);

    /**
     * 新增试卷（主键自动回填到 paper.id）
     */
    @Insert("INSERT INTO exam_paper(paper_name, course_id, paper_type, total_score, duration, " +
            "pass_score, creator_id, status, is_deleted) " +
            "VALUES(#{paperName}, #{courseId}, #{paperType}, #{totalScore}, #{duration}, " +
            "#{passScore}, #{creatorId}, #{status}, 0)")
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ExamPaper paper);

    /** 更新试卷基本信息（按 id）；不含 pass_score —— 及格分由总分统一换算，不再人工编辑 */
    @Update("UPDATE exam_paper SET paper_name=#{paperName}, course_id=#{courseId}, " +
            "paper_type=#{paperType}, duration=#{duration} " +
            "WHERE id=#{id} AND is_deleted = 0")
    int updateById(ExamPaper paper);

    /** 更新试卷状态（0草稿 1已发布 2已归档） */
    @Update("UPDATE exam_paper SET status=#{status} WHERE id=#{id} AND is_deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 更新试卷总分（组卷保存后自动计算），并同步及格分
     *
     * <p>及格分统一口径：满分 × 60%（保留 1 位小数），与班级学情统计的及格线一致，
     * 避免出现"统计按 60% 判及格、成绩单按人工填的固定分数判"两套标准互相矛盾。</p>
     */
    @Update("UPDATE exam_paper SET total_score=#{totalScore}, " +
            "pass_score=ROUND(#{totalScore} * 0.6, 1) WHERE id=#{id}")
    int updateTotalScore(@Param("id") Long id, @Param("totalScore") java.math.BigDecimal totalScore);

    /** 逻辑删除试卷（保留历史考试成绩数据） */
    @Update("UPDATE exam_paper SET is_deleted = 1 WHERE id = #{id}")
    int logicDeleteById(@Param("id") Long id);

    /** 物理删除试卷（仅无历史考试记录时使用） */
    @Delete("DELETE FROM exam_paper WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /** 批量逻辑删除试卷 */
    @Update("<script>" +
            "UPDATE exam_paper SET is_deleted = 1 WHERE is_deleted = 0 AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int logicDeleteByIds(@Param("ids") List<Long> ids);

    /** 批量物理删除试卷 */
    @Delete("<script>" +
            "DELETE FROM exam_paper WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int deleteByIds(@Param("ids") List<Long> ids);

    /** 分页 + 可选条件查询（带题数统计；非管理员只看自己任教课程下、自己创建或被共享的；过滤逻辑删除） */
    @SelectProvider(type = ExamPaperMapper.PaperSqlProvider.class, method = "listByQuery")
    List<ExamPaper> listByQuery(@Param("offset") int offset, @Param("pageSize") int pageSize,
                                @Param("paperName") String paperName, @Param("status") Integer status,
                                @Param("courseId") Long courseId,
                                @Param("userId") Long userId, @Param("role") Integer role,
                                @Param("courseIds") List<Long> courseIds);

    /** 分页查询对应的总数 */
    @SelectProvider(type = ExamPaperMapper.PaperSqlProvider.class, method = "countByQuery")
    long countByQuery(@Param("paperName") String paperName, @Param("status") Integer status,
                      @Param("courseId") Long courseId,
                      @Param("userId") Long userId, @Param("role") Integer role,
                      @Param("courseIds") List<Long> courseIds);

    /** 按 ID 统计关联考试数（含历史已结束，用于删除前判断走逻辑删还是物理删） */
    @Select("SELECT COUNT(*) FROM exam WHERE paper_id = #{paperId}")
    int countExamsByPaperId(@Param("paperId") Long paperId);

    /** 按试卷名称查同名校验（新增/重名校验，排除逻辑删除） */
    @Select("SELECT COUNT(*) FROM exam_paper WHERE paper_name = #{paperName} AND is_deleted = 0")
    int countByName(@Param("paperName") String paperName);

    /** 内部类：动态 SQL 生成器 */
    class PaperSqlProvider {

        /** 公共 WHERE 片段（可见性：管理员全部；教师=自己创建 OR 被共享给自己的；过滤逻辑删除） */
        private String whereClause(String paperName, Integer status, Long courseId,
                                   Long userId, Integer role, List<Long> courseIds) {
            StringBuilder sb = new StringBuilder(" WHERE p.is_deleted = 0 ");
            if (paperName != null && !paperName.isBlank()) {
                sb.append("AND p.paper_name LIKE CONCAT('%', #{paperName}, '%') ");
            }
            if (status != null) {
                sb.append("AND p.status = #{status} ");
            }
            if (courseId != null) {
                sb.append("AND p.course_id = #{courseId} ");
            }
            // 数据权限：教师只能看到自己任教课程下的试卷（由 TeacherScope 注入，管理员为 null 不加条件）
            if (courseIds != null && !courseIds.isEmpty()) {
                sb.append("AND p.course_id IN (");
                for (int i = 0; i < courseIds.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append("#{courseIds[").append(i).append("]}");
                }
                sb.append(") ");
            }
            // 创建人隔离：管理员(role=2)看全部；其他角色只看自己创建的 + 被共享给自己的
            if (role == null || role != 2) {
                sb.append("AND (p.creator_id = #{userId} ")
                  .append("OR EXISTS (SELECT 1 FROM exam_paper_visible_user v " +
                          "WHERE v.paper_id = p.id AND v.user_id = #{userId})) ");
            }
            return sb.toString();
        }

        public String listByQuery(@Param("offset") int offset, @Param("pageSize") int pageSize,
                                  @Param("paperName") String paperName, @Param("status") Integer status,
                                  @Param("courseId") Long courseId,
                                  @Param("userId") Long userId, @Param("role") Integer role,
                                  @Param("courseIds") List<Long> courseIds) {
            return "SELECT p.*, c.course_name AS course_name, u.real_name AS creator_name, " +
                    "(SELECT COUNT(*) FROM paper_question pq WHERE pq.paper_id = p.id) AS question_count " +
                    "FROM exam_paper p LEFT JOIN sys_course c ON p.course_id = c.id " +
                    "LEFT JOIN sys_user u ON p.creator_id = u.id" +
                    whereClause(paperName, status, courseId, userId, role, courseIds) +
                    "ORDER BY p.id DESC LIMIT #{pageSize} OFFSET #{offset}";
        }

        public String countByQuery(@Param("paperName") String paperName, @Param("status") Integer status,
                                   @Param("courseId") Long courseId,
                                   @Param("userId") Long userId, @Param("role") Integer role,
                                   @Param("courseIds") List<Long> courseIds) {
            return "SELECT COUNT(*) FROM exam_paper p" +
                    whereClause(paperName, status, courseId, userId, role, courseIds);
        }
    }
}
