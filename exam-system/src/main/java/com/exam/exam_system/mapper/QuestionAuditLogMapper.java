package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.QuestionAuditLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 试题审核流水 Mapper，对应 question_audit_log 表
 *
 * <p>流水为留痕记录：仅支持插入与查询，不提供修改/删除方法（防止篡改审核历史）。
 */
@Mapper
public interface QuestionAuditLogMapper {

    /**
     * 插入一条审核流水（审核通过 / 驳回 / 教师重新提交时调用）
     */
    @Insert("INSERT INTO question_audit_log(question_id, action, opinion, content_snapshot, " +
            "submitter_id, auditor_id, audit_time) " +
            "VALUES(#{questionId}, #{action}, #{opinion}, #{contentSnapshot}, " +
            "#{submitterId}, #{auditorId}, #{auditTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(QuestionAuditLog log);

    /**
     * 审核流水列表（分页 + 多条件筛选）
     *
     * <p>筛选说明：
     * <ul>
     *   <li>action：审核动作 1通过 2驳回 3重新提交（null=全部）</li>
     *   <li>questionType / difficulty：联 question 表按题型/难度筛</li>
     *   <li>creatorId：按提交教师筛（匹配流水的 submitter_id）</li>
     *   <li>auditUserId：按审核操作人筛（重新提交流水无审核人，自动排除）</li>
     *   <li>keyword：题干关键词（同时匹配题干快照与题目当前题干）</li>
     *   <li>startTime / endTime：按流水操作时间筛</li>
     * </ul>
     */
    @Select("<script>" +
            "SELECT l.*, " +
            "q.question_type, q.difficulty, q.create_time, " +
            "c.course_name, " +
            "submitter.username AS submitter_name, " +
            "auditor.username AS auditor_name " +
            "FROM question_audit_log l " +
            "LEFT JOIN question q ON l.question_id = q.id " +
            "LEFT JOIN sys_course c ON q.course_id = c.id " +
            "LEFT JOIN sys_user submitter ON l.submitter_id = submitter.id " +
            "LEFT JOIN sys_user auditor ON l.auditor_id = auditor.id " +
            "WHERE 1=1 " +
            "<if test='action != null'>AND l.action = #{action} </if>" +
            "<if test='questionType != null'>AND q.question_type = #{questionType} </if>" +
            "<if test='difficulty != null'>AND q.difficulty = #{difficulty} </if>" +
            "<if test='creatorId != null'>AND l.submitter_id = #{creatorId} </if>" +
            "<if test='auditUserId != null'>AND l.auditor_id = #{auditUserId} </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (l.content_snapshot LIKE CONCAT('%', #{keyword}, '%') " +
            "OR q.content LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "<if test='startTime != null and startTime != \"\"'>AND l.audit_time &gt;= #{startTime} </if>" +
            "<if test='endTime != null and endTime != \"\"'>AND l.audit_time &lt;= #{endTime} </if>" +
            "ORDER BY l.audit_time DESC, l.id DESC " +
            "LIMIT #{offset}, #{pageSize}" +
            "</script>")
    List<QuestionAuditLog> listByFilter(@Param("action") Integer action,
                                        @Param("questionType") Integer questionType,
                                        @Param("difficulty") Integer difficulty,
                                        @Param("creatorId") Long creatorId,
                                        @Param("auditUserId") Long auditUserId,
                                        @Param("keyword") String keyword,
                                        @Param("startTime") String startTime,
                                        @Param("endTime") String endTime,
                                        @Param("offset") int offset,
                                        @Param("pageSize") int pageSize);

    /**
     * 审核流水计数（与 listByFilter 筛选条件一致）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM question_audit_log l " +
            "LEFT JOIN question q ON l.question_id = q.id " +
            "WHERE 1=1 " +
            "<if test='action != null'>AND l.action = #{action} </if>" +
            "<if test='questionType != null'>AND q.question_type = #{questionType} </if>" +
            "<if test='difficulty != null'>AND q.difficulty = #{difficulty} </if>" +
            "<if test='creatorId != null'>AND l.submitter_id = #{creatorId} </if>" +
            "<if test='auditUserId != null'>AND l.auditor_id = #{auditUserId} </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (l.content_snapshot LIKE CONCAT('%', #{keyword}, '%') " +
            "OR q.content LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "<if test='startTime != null and startTime != \"\"'>AND l.audit_time &gt;= #{startTime} </if>" +
            "<if test='endTime != null and endTime != \"\"'>AND l.audit_time &lt;= #{endTime} </if>" +
            "</script>")
    long countByFilter(@Param("action") Integer action,
                       @Param("questionType") Integer questionType,
                       @Param("difficulty") Integer difficulty,
                       @Param("creatorId") Long creatorId,
                       @Param("auditUserId") Long auditUserId,
                       @Param("keyword") String keyword,
                       @Param("startTime") String startTime,
                       @Param("endTime") String endTime);

    /**
     * 按动作统计流水数量（统计卡片用）
     *
     * @return [{action: 1, cnt: 10}, {action: 2, cnt: 3}, ...]
     */
    @Select("SELECT action, COUNT(*) AS cnt FROM question_audit_log GROUP BY action")
    List<Map<String, Object>> countByAction();

    /**
     * 查询某道题的全部审核流水（时间正序，详情弹窗时间线用）
     */
    @Select("SELECT l.*, " +
            "submitter.username AS submitter_name, " +
            "auditor.username AS auditor_name " +
            "FROM question_audit_log l " +
            "LEFT JOIN sys_user submitter ON l.submitter_id = submitter.id " +
            "LEFT JOIN sys_user auditor ON l.auditor_id = auditor.id " +
            "WHERE l.question_id = #{questionId} " +
            "ORDER BY l.audit_time ASC, l.id ASC")
    List<QuestionAuditLog> listByQuestionId(@Param("questionId") Long questionId);
}
