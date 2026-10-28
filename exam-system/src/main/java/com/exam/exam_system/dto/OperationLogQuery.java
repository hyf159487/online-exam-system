package com.exam.exam_system.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志查询参数（管理员审计页用）
 *
 * <p>支持多条件组合筛选：
 * <ul>
 *   <li>按操作人账号模糊搜索</li>
 *   <li>按操作模块 / 操作类型 / 操作状态精确筛选</li>
 *   <li>按操作时间范围筛选</li>
 * </ul>
 * 通过 {@link #getOffset()} 计算分页偏移量，{@link #getLimitSize()} 限制 pageSize 上限。
 */
@Data
public class OperationLogQuery {

    /** 第几页（从 1 开始），默认 1 */
    private Integer pageNum = 1;

    /** 每页多少条，默认 10，上限 100 */
    private Integer pageSize = 10;

    /** 按操作人账号模糊搜索（可选） */
    private String operatorName;

    /** 按操作模块精确筛选（可选）：题库管理/试卷管理/考试管理/账号管理/试题审核管理/系统管理 */
    private String module;

    /** 按操作类型精确筛选（可选）：新增/修改/删除/批量删除/导入/发布考试 等 */
    private String operationType;

    /** 按操作状态精确筛选（可选）：1=成功 0=失败 */
    private Integer status;

    /** 起始时间（可选，含），格式 yyyy-MM-dd HH:mm:ss */
    private LocalDateTime startTime;

    /** 截止时间（可选，含），格式 yyyy-MM-dd HH:mm:ss */
    private LocalDateTime endTime;

    /** MyBatis 动态 SQL 用：分页偏移 = (页码-1) * 每页条数 */
    public int getOffset() {
        int pn = pageNum == null ? 1 : pageNum;
        int ps = pageSize == null ? 10 : pageSize;
        return Math.max(0, (pn - 1) * ps);
    }

    /** 限制每页最大 100 条，避免一次拉太多 */
    public int getLimitSize() {
        int ps = pageSize == null ? 10 : pageSize;
        return Math.min(ps, 100);
    }
}
