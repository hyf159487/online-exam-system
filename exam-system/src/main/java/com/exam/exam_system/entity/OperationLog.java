package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体，对应 operation_log 表
 *
 * <p>审计数据：只增不改。所有写操作（新增/修改/删除/批量/导入/发布等）
 * 由 AOP 切面 {@code OperationLogAspect} 自动记录，普通查询不记录。
 *
 * <p>字段对照（数据库 → 实体属性，MyBatis 驼峰自动映射）：
 * <pre>
 * id              → id              日志ID
 * operator_id     → operatorId      操作人ID
 * operator_name   → operatorName    操作人账号
 * module          → module          操作模块
 * operation_type  → operationType   操作类型
 * description     → description     操作描述
 * business_id     → businessId      业务ID（字符串，支持多ID逗号分隔）
 * request_url     → requestUrl      请求地址
 * request_method  → requestMethod   请求方式
 * client_ip       → clientIp        客户端IP
 * status          → status          操作状态：1=成功 0=失败
 * error_msg       → errorMsg        失败异常信息
 * cost_ms         → costMs          接口耗时（毫秒）
 * create_time     → createTime      操作时间
 * </pre>
 */
@Data
public class OperationLog {

    private Long id;

    /** 操作人ID（sys_user.id） */
    private Long operatorId;

    /** 操作人账号/姓名 */
    private String operatorName;

    /** 操作模块：题库管理/试卷管理/考试管理/账号管理/试题审核管理/系统管理 */
    private String module;

    /** 操作类型：新增/修改/删除/批量删除/批量新增/导入/导出/发布考试/终止考试/重置密码/权限变更 */
    private String operationType;

    /** 操作描述（含业务数据摘要） */
    private String description;

    /** 业务ID（字符串，支持多ID逗号分隔） */
    private String businessId;

    /** 请求地址 */
    private String requestUrl;

    /** 请求方式：GET/POST/PUT/DELETE */
    private String requestMethod;

    /** 客户端IP */
    private String clientIp;

    /** 操作状态：1=成功 0=失败 */
    private Integer status;

    /** 失败异常信息（成功为 null） */
    private String errorMsg;

    /** 接口耗时（毫秒） */
    private Integer costMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
