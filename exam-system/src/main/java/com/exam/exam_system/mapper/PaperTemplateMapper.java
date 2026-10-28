package com.exam.exam_system.mapper;

import com.exam.exam_system.dto.PaperTemplateVO;
import com.exam.exam_system.entity.PaperTemplate;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 组卷模板 Mapper，对应 paper_template 表
 *
 * <p>列表查询带课程名 + 创建人名联表，分页用 @SelectProvider 动态 SQL。
 */
@Mapper
public interface PaperTemplateMapper {

    /** 按 ID 查单个模板 */
    @Select("SELECT * FROM paper_template WHERE id = #{id}")
    PaperTemplate selectById(@Param("id") Long id);

    /** 新增模板，主键自动回填 */
    @Insert("INSERT INTO paper_template(template_name, course_id, total_score, total_question_count, " +
            "template_json, creator_id, remark, paper_type, ai_requirement) " +
            "VALUES(#{templateName}, #{courseId}, #{totalScore}, #{totalQuestionCount}, " +
            "#{templateJson}, #{creatorId}, #{remark}, #{paperType}, #{aiRequirement})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PaperTemplate template);

    /** 更新模板 */
    @Update("UPDATE paper_template SET template_name=#{templateName}, course_id=#{courseId}, " +
            "total_score=#{totalScore}, total_question_count=#{totalQuestionCount}, " +
            "template_json=#{templateJson}, remark=#{remark}, paper_type=#{paperType}, " +
            "ai_requirement=#{aiRequirement} WHERE id=#{id}")
    int updateById(PaperTemplate template);

    /** 删除模板 */
    @Delete("DELETE FROM paper_template WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /** 分页查询模板列表（联表课程、创建人），支持按名称/课程筛选 */
    @SelectProvider(type = PaperTemplateMapper.TemplateSqlProvider.class, method = "listByQuery")
    List<PaperTemplateVO> listByQuery(@Param("offset") int offset,
                                      @Param("pageSize") int pageSize,
                                      @Param("templateName") String templateName,
                                      @Param("courseId") Long courseId,
                                      @Param("creatorId") Long creatorId,
                                      @Param("role") Integer role);

    /** 计数查询 */
    @SelectProvider(type = PaperTemplateMapper.TemplateSqlProvider.class, method = "countByQuery")
    long countByQuery(@Param("templateName") String templateName,
                      @Param("courseId") Long courseId,
                      @Param("creatorId") Long creatorId,
                      @Param("role") Integer role);

    /** 动态 SQL 生成器 */
    class TemplateSqlProvider {

        private String whereClause(String templateName, Long courseId,
                                   Long creatorId, Integer role) {
            StringBuilder sb = new StringBuilder(" WHERE 1=1 ");
            if (templateName != null && !templateName.isBlank()) {
                sb.append("AND pt.template_name LIKE CONCAT('%', #{templateName}, '%') ");
            }
            if (courseId != null) {
                sb.append("AND pt.course_id = #{courseId} ");
            }
            // 教师仅看自己的模板；管理员看全部
            if (role != null && role == 1 && creatorId != null) {
                sb.append("AND pt.creator_id = #{creatorId} ");
            }
            return sb.toString();
        }

        public String listByQuery(@Param("offset") int offset,
                                  @Param("pageSize") int pageSize,
                                  @Param("templateName") String templateName,
                                  @Param("courseId") Long courseId,
                                  @Param("creatorId") Long creatorId,
                                  @Param("role") Integer role) {
            return "SELECT pt.id, pt.template_name AS templateName, " +
                    "pt.course_id AS courseId, c.course_name AS courseName, " +
                    "pt.total_score AS totalScore, pt.total_question_count AS totalQuestionCount, " +
                    "pt.paper_type AS paperType, " +
                    "pt.creator_id AS creatorId, u.username AS creatorName, " +
                    "pt.create_time AS createTime, pt.remark " +
                    "FROM paper_template pt " +
                    "LEFT JOIN sys_course c ON pt.course_id = c.id " +
                    "LEFT JOIN sys_user u ON pt.creator_id = u.id" +
                    whereClause(templateName, courseId, creatorId, role) +
                    "ORDER BY pt.create_time DESC LIMIT #{pageSize} OFFSET #{offset}";
        }

        public String countByQuery(@Param("templateName") String templateName,
                                   @Param("courseId") Long courseId,
                                   @Param("creatorId") Long creatorId,
                                   @Param("role") Integer role) {
            return "SELECT COUNT(*) FROM paper_template pt" +
                    whereClause(templateName, courseId, creatorId, role);
        }
    }
}
