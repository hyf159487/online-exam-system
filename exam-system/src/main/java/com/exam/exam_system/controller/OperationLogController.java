package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.OperationLogQuery;
import com.exam.exam_system.entity.OperationLog;
import com.exam.exam_system.service.OperationLogService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 操作日志 Controller（管理员专属审计页面）
 *
 * <p>所有接口需登录 + 管理员权限（由 AuthInterceptor 拦截器控制：
 * /api/operation-log/** 仅 role=2 可访问）。
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET  /api/operation-log/list           分页查询</li>
 *   <li>GET  /api/operation-log/{id}           查单条（详情弹窗）</li>
 *   <li>GET  /api/operation-log/modules        模块下拉选项</li>
 *   <li>GET  /api/operation-log/operationTypes  操作类型下拉选项</li>
 *   <li>GET  /api/operation-log/export         导出 Excel</li>
 * </ul>
 *
 * <p>❗禁止任何 DELETE / PUT / POST 修改日志：审计数据只增不改。
 */
@RestController
@RequestMapping("/api/operation-log")
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 分页查询操作日志
     *
     * <p>示例：
     * <pre>
     * GET /api/operation-log/list?pageNum=1&pageSize=10&module=题库管理&status=1
     *     &startTime=2026-08-01 00:00:00&endTime=2026-08-31 23:59:59
     * </pre>
     */
    @GetMapping("/list")
    public Result<PageResult<OperationLog>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        OperationLogQuery q = new OperationLogQuery();
        q.setPageNum(pageNum);
        q.setPageSize(pageSize);
        q.setOperatorName(operatorName);
        q.setModule(module);
        q.setOperationType(operationType);
        q.setStatus(status);
        try {
            if (startTime != null && !startTime.isBlank()) {
                q.setStartTime(LocalDateTime.parse(startTime, DT_FMT));
            }
            if (endTime != null && !endTime.isBlank()) {
                q.setEndTime(LocalDateTime.parse(endTime, DT_FMT));
            }
        } catch (Exception e) {
            return Result.error(400, "时间格式错误，应为 yyyy-MM-dd HH:mm:ss");
        }

        PageResult<OperationLog> result = operationLogService.page(q);
        return Result.success("查询成功", result);
    }

    /**
     * 按 ID 查单条操作日志（详情弹窗用）
     * GET /api/operation-log/{id}
     */
    @GetMapping("/{id}")
    public Result<OperationLog> getById(@PathVariable Long id) {
        OperationLog log = operationLogService.getById(id);
        if (log == null) {
            return Result.error(404, "日志不存在");
        }
        return Result.success("查询成功", log);
    }

    /** 模块下拉选项 */
    @GetMapping("/modules")
    public Result<List<String>> modules() {
        return Result.success(operationLogService.listModules());
    }

    /** 操作类型下拉选项 */
    @GetMapping("/operationTypes")
    public Result<List<String>> operationTypes() {
        return Result.success(operationLogService.listOperationTypes());
    }

    /**
     * 导出 Excel（当前筛选条件下的日志）
     * GET /api/operation-log/export?...
     * 直接返回 .xlsx 文件流，浏览器触发下载
     */
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            HttpServletResponse response) {

        OperationLogQuery q = new OperationLogQuery();
        q.setPageNum(1);
        q.setPageSize(10000);   // 导出上限
        q.setOperatorName(operatorName);
        q.setModule(module);
        q.setOperationType(operationType);
        q.setStatus(status);
        try {
            if (startTime != null && !startTime.isBlank()) {
                q.setStartTime(LocalDateTime.parse(startTime, DT_FMT));
            }
            if (endTime != null && !endTime.isBlank()) {
                q.setEndTime(LocalDateTime.parse(endTime, DT_FMT));
            }
        } catch (Exception ignored) {
            // 时间格式错误，忽略过滤条件继续导出
        }

        operationLogService.export(q, response);
    }
}
