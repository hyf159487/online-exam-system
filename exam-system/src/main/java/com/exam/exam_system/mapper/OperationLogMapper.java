package com.exam.exam_system.mapper;

import com.exam.exam_system.dto.OperationLogQuery;
import com.exam.exam_system.entity.OperationLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 操作日志 Mapper，对应 operation_log 表
 *
 * <p>仅提供 INSERT 和 SELECT，不提供 UPDATE/DELETE，
 * 保证审计数据不可篡改。
 *
 * <p>复杂多条件分页查询使用 {@link SqlProvider} 动态拼条件，
 * 与项目其他 Mapper（SysUserMapper/QuestionMapper）风格一致。
 */
@Mapper
public interface OperationLogMapper {

    /**
     * 插入一条操作日志（由 AOP 切面调用）
     * 不插 id / create_time（DB 自增 / 默认值）
     */
    @Insert("INSERT INTO operation_log " +
            "(operator_id, operator_name, module, operation_type, description, " +
            " business_id, request_url, request_method, client_ip, status, error_msg, cost_ms) " +
            "VALUES " +
            "(#{operatorId}, #{operatorName}, #{module}, #{operationType}, #{description}, " +
            " #{businessId}, #{requestUrl}, #{requestMethod}, #{clientIp}, #{status}, #{errorMsg}, #{costMs})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OperationLog log);

    /** 按 ID 查单条（详情弹窗用） */
    @Select("SELECT * FROM operation_log WHERE id = #{id} LIMIT 1")
    OperationLog selectById(@Param("id") Long id);

    /**
     * 分页查日志 + 多条件筛选
     * 默认按操作时间倒序（最新记录置顶）
     */
    @SelectProvider(type = SqlProvider.class, method = "listByQuery")
    List<OperationLog> listByQuery(@Param("q") OperationLogQuery q);

    /** 配合 listByQuery 的总数查询 */
    @SelectProvider(type = SqlProvider.class, method = "countByQuery")
    long countByQuery(@Param("q") OperationLogQuery q);

    /** 导出用：按筛选条件拉全部（前端传 pageSize 上限 10000） */
    @SelectProvider(type = SqlProvider.class, method = "listByQuery")
    List<OperationLog> listForExport(@Param("q") OperationLogQuery q);

    /** 动态 SQL 提供器 */
    class SqlProvider {
        public String listByQuery(java.util.Map<String, Object> params) {
            OperationLogQuery q = (OperationLogQuery) params.get("q");
            StringBuilder sb = new StringBuilder("SELECT * FROM operation_log WHERE 1=1 ");
            appendConditions(sb, q);
            sb.append(" ORDER BY create_time DESC, id DESC LIMIT #{q.offset}, #{q.limitSize}");
            return sb.toString();
        }

        public String countByQuery(java.util.Map<String, Object> params) {
            OperationLogQuery q = (OperationLogQuery) params.get("q");
            StringBuilder sb = new StringBuilder("SELECT COUNT(*) FROM operation_log WHERE 1=1 ");
            appendConditions(sb, q);
            return sb.toString();
        }

        private void appendConditions(StringBuilder sb, OperationLogQuery q) {
            if (q == null) return;
            if (q.getOperatorName() != null && !q.getOperatorName().trim().isEmpty()) {
                sb.append(" AND operator_name LIKE CONCAT('%', #{q.operatorName}, '%') ");
            }
            if (q.getModule() != null && !q.getModule().trim().isEmpty()) {
                sb.append(" AND module = #{q.module} ");
            }
            if (q.getOperationType() != null && !q.getOperationType().trim().isEmpty()) {
                sb.append(" AND operation_type = #{q.operationType} ");
            }
            if (q.getStatus() != null) {
                sb.append(" AND status = #{q.status} ");
            }
            if (q.getStartTime() != null) {
                sb.append(" AND create_time >= #{q.startTime} ");
            }
            if (q.getEndTime() != null) {
                sb.append(" AND create_time <= #{q.endTime} ");
            }
        }
    }
}
