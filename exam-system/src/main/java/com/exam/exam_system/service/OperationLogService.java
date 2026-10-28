package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.OperationLogQuery;
import com.exam.exam_system.entity.OperationLog;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * 操作日志服务
 *
 * <p>核心方法：
 * <ul>
 *   <li>{@link #page}      分页查询（管理员审计页用）</li>
 *   <li>{@link #getById}   查单条（详情弹窗）</li>
 *   <li>{@link #record}   异步记录日志（AOP 切面调用，业务接口不影响）</li>
 *   <li>{@link #export}   导出当前筛选条件下的日志为 Excel</li>
 * </ul>
 */
public interface OperationLogService {

    /** 分页查询操作日志（按时间倒序） */
    PageResult<OperationLog> page(OperationLogQuery query);

    /** 按 ID 查单条操作日志 */
    OperationLog getById(Long id);

    /**
     * 异步记录一条操作日志（AOP 切面调用）
     *
     * <p>异步执行，不阻塞业务接口；记录失败仅打印日志，
     * 不会影响业务调用本身（审计日志不是业务关键路径）。
     */
    @Async
    void record(OperationLog log);

    /**
     * 把当前筛选条件下的日志导出为 .xlsx，直接写入 response 流
     *
     * @param query    筛选条件（导出全部时 pageNum=1, pageSize=10000）
     * @param response HttpServletResponse，用于直接写出文件流
     */
    void export(OperationLogQuery query, HttpServletResponse response);

    /** 查询支持的模块列表（下拉选项用） */
    List<String> listModules();

    /** 查询支持的操作类型列表（下拉选项用） */
    List<String> listOperationTypes();
}
