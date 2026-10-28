package com.exam.exam_system.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解：打在 Controller 方法上，由 {@code OperationLogAspect} 拦截自动记录。
 *
 * <p>使用示例：
 * <pre>{@code
 * @OperationLog(module = "题库管理", type = "删除", description = "删除 id=#{#id} 试题")
 * @DeleteMapping("/{id}")
 * public Result<Void> delete(@PathVariable Long id) { ... }
 *
 * @OperationLog(module = "题库管理", type = "新增", description = "新增 #{#q.questionType} 题：#{#q.content}")
 * @PostMapping
 * public Result<Long> add(@RequestBody Question q) { ... }
 *
 * @OperationLog(module = "题库管理", type = "批量删除", description = "批量删除 #{#ids.size()} 题：#{#ids}")
 * @PostMapping("/batchDelete")
 * public Result<Void> batchDelete(@RequestBody List<Long> ids) { ... }
 * }</pre>
 *
 * <p>SpEL 上下文可用变量：
 * <ul>
 *   <li>{@code #参数名}：方法形参（如 {@code #id}、{@code #q}、{@code #ids}）</li>
 *   <li>{@code #result}：方法返回值（仅 @AfterReturning 可用）</li>
 *   <li>{@code #throwable}：异常对象（仅 @AfterThrowing 可用）</li>
 * </ul>
 *
 * <p>未指定 description 时，切面会自动生成简化描述（module + type）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 操作模块：题库管理/试卷管理/考试管理/账号管理/试题审核管理/系统管理 等 */
    String module();

    /** 操作类型：新增/修改/删除/批量删除/导入/发布考试/重置密码 等 */
    String type();

    /**
     * 操作描述模板（SpEL 表达式，可选）：
     * 如 {@code "删除 id=#{#id} 试题"}、{@code "新增题目：#{#q.content}"}
     * 为空字符串时自动用 "{module}{type}" 兜底
     */
    String description() default "";
}
