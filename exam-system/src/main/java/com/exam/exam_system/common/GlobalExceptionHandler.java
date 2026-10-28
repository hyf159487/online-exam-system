package com.exam.exam_system.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理器
 *
 * <p>把 Service/Controller 层抛出的异常统一转成 {code, msg, data} 响应格式，
 * 避免 Spring 默认返回 500 HTML 错误页，导致前端只能看到"服务器错误"而不知道具体原因。
 *
 * <p>约定（与 Result 一致）：
 * <ul>
 *     <li>RuntimeException（业务异常，如"题库不足"）→ code=400，msg 为具体业务提示</li>
 *     <li>Exception（未预期的系统错误）→ code=500，不向外部暴露内部细节</li>
 * </ul>
 *
 * <p>注意：AuthInterceptor 的 401/403 是自行写响应（不抛异常），不受本类影响。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** multipart 单文件上限（取自 application.properties 的 spring.servlet.multipart.max-file-size），用于超限提示 */
    @Value("${spring.servlet.multipart.max-file-size:200MB}")
    private String maxFileSize;

    /** 上传文件超过大小限制：返回明确提示，避免前端收不到响应而误报"网络异常/服务器错误" */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制 {}: {}", maxFileSize, e.getMessage());
        return Result.error(400, "上传失败：单个文件不能超过 " + maxFileSize);
    }

    /**
     * 数据越权异常：教师访问/操作了不属于自己任教课程的数据
     * → 统一返回 403，前端按"无权限"处理（与登录拦截器的 403 语义一致）
     */
    @ExceptionHandler(ForbiddenException.class)
    public Result<Void> handleForbidden(ForbiddenException e) {
        log.warn("数据越权: {}", e.getMessage());
        return Result.error(403, e.getMessage());
    }

    /** 携带结构化数据的业务异常（如"题库不足"），data 供前端做后续操作 */
    @ExceptionHandler(BizException.class)
    public Result<Object> handleBizException(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(400, e.getMessage(), e.getData());
    }

    /** 业务异常：把 message 原样返回，前端可直接展示 */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    /** 兜底异常：记录完整堆栈，对外只返回通用提示 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "系统繁忙，请稍后重试");
    }
}
