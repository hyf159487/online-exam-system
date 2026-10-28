package com.exam.exam_system.aspect;

import com.exam.exam_system.entity.OperationLog;
import com.exam.exam_system.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 操作日志切面：拦截所有标注 {@link OperationLog} 的方法，
 * 在方法返回/抛异常后异步记录一条审计日志。
 *
 * <p>核心特性：
 * <ul>
 *   <li>使用 @Around 一次性获取返回值/异常/耗时</li>
 *   <li>SpEL 解析 description 模板，支持 {@code #{#param}} / {@code #{#result}}</li>
 *   <li>从 request attribute 取操作人信息（AuthInterceptor 已注入 userId/username）</li>
 *   <li>耗时统计、异常信息截断</li>
 *   <li>切面内部 catch 所有异常，绝不影响业务调用</li>
 * </ul>
 */
@Aspect
@Component
@Slf4j
public class OperationLogAspect {

    @Autowired
    private OperationLogService operationLogService;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramNameDiscoverer =
            new DefaultParameterNameDiscoverer();

    @Around("@annotation(com.exam.exam_system.annotation.OperationLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable thrown = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            thrown = t;
            throw t;       // 异常继续向外抛，业务调用方处理
        } finally {
            // 即使切面本身报错也不影响业务
            try {
                record(joinPoint, result, thrown, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("操作日志切面记录失败：{}", e.getMessage(), e);
            }
        }
    }

    private void record(ProceedingJoinPoint joinPoint, Object result, Throwable thrown, long costMs) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        com.exam.exam_system.annotation.OperationLog annotation =
                method.getAnnotation(com.exam.exam_system.annotation.OperationLog.class);
        if (annotation == null) return;

        // 取当前请求上下文（AuthInterceptor 已把 userId/username 挂在 request 属性上）
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return;       // 非 HTTP 上下文，跳过
        HttpServletRequest request = attrs.getRequest();

        OperationLog entity = new OperationLog();
        entity.setModule(annotation.module());
        entity.setOperationType(annotation.type());
        entity.setOperatorId((Long) request.getAttribute("userId"));
        entity.setOperatorName((String) request.getAttribute("username"));
        entity.setRequestUrl(request.getRequestURI());
        entity.setRequestMethod(request.getMethod());
        entity.setClientIp(getClientIp(request));
        entity.setCostMs((int) costMs);

        // SpEL 解析描述
        String desc = evaluateDescription(annotation, joinPoint, result, thrown);
        entity.setDescription(desc);

        // 提取业务 ID（返回值/方法参数）
        String bizId = extractBusinessId(method, joinPoint.getArgs(), result);
        entity.setBusinessId(bizId);

        // 成功/失败 + 异常信息
        if (thrown != null) {
            entity.setStatus(0);
            String msg = thrown.getMessage();
            if (msg != null && msg.length() > 1000) msg = msg.substring(0, 1000);
            entity.setErrorMsg(msg);
        } else {
            entity.setStatus(1);
        }

        // 异步记录：业务接口不等日志入库
        operationLogService.record(entity);
    }

    /**
     * 用 SpEL 解析 description 模板。
     * 上下文变量：所有方法形参（按参数名）+ {@code #result}（返回值）+ {@code #throwable}（异常）
     */
    private String evaluateDescription(com.exam.exam_system.annotation.OperationLog annotation,
                                       ProceedingJoinPoint joinPoint,
                                       Object result, Throwable thrown) {
        String tpl = annotation.description();
        if (tpl == null || tpl.isBlank()) {
            // 兜底：模块 + 类型
            return annotation.module() + " - " + annotation.type();
        }
        // 没有 #{} 占位符就直接用模板字符串
        if (!tpl.contains("#{") && !tpl.contains("${")) {
            return tpl;
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            EvaluationContext ctx = new MethodBasedEvaluationContext(
                    joinPoint.getTarget(), method, joinPoint.getArgs(), paramNameDiscoverer);
            ctx.setVariable("result", result);
            ctx.setVariable("throwable", thrown);
            ctx.setVariable("args", joinPoint.getArgs());

            // 替换 #{...} 占位符（兼容 ${...}）
            String result2 = tpl;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("#\\{([^}]+)}")
                    .matcher(tpl);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String expr = m.group(1).trim();
                Object val = null;
                try {
                    val = parser.parseExpression(expr).getValue(ctx);
                } catch (Exception e) {
                    log.warn("操作日志 SpEL 解析失败：{} -> {}", expr, e.getMessage());
                }
                String replacement = val == null ? "" : truncate(val.toString(), 500);
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
            }
            m.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            log.warn("操作日志 description 解析失败: {}", e.getMessage());
            return tpl;
        }
    }

    /**
     * 业务 ID 提取策略（按优先级）：
     * 1. 返回值为 Result，且 data 是 Number → 用 data 作为 businessId（新增返回 ID）
     * 2. 第一个参数为 Long → 用作 businessId（按 ID 删除/更新）
     * 3. 第一个参数为实体对象且有 getId 方法 → 用 id（更新）
     * 4. 第一个参数为 List → 取前几个 ID 拼接（批量删除）
     * 5. 否则 null
     */
    private String extractBusinessId(Method method, Object[] args, Object result) {
        try {
            // 策略 1：返回值 Result<Long> 或 List<Long>
            if (result != null) {
                Object data = unwrapResult(result);
                if (data instanceof Number) {
                    return String.valueOf(data);
                }
                if (data instanceof List<?> list) {
                    if (!list.isEmpty() && list.get(0) instanceof Number) {
                        return joinIds(list, 50);
                    }
                }
            }
            // 策略 2/3/4：检查第一个参数
            if (args == null || args.length == 0) return null;
            Object first = args[0];
            if (first instanceof Number n) {
                return String.valueOf(n);
            }
            if (first instanceof List<?> list) {
                if (list.isEmpty()) return null;
                if (list.get(0) instanceof Number) {
                    return joinIds(list, 50);
                }
                // 实体列表，提取前 N 个的 id
                StringBuilder sb = new StringBuilder();
                int cnt = 0;
                for (Object item : list) {
                    Object id = tryGetId(item);
                    if (id != null) {
                        if (sb.length() > 0) sb.append(",");
                        sb.append(id);
                        if (++cnt >= 50) { sb.append("..."); break; }
                    }
                }
                return sb.length() == 0 ? null : sb.toString();
            }
            // 策略 3：单个实体
            Object id = tryGetId(first);
            return id == null ? null : String.valueOf(id);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 Result 包装里取 data 字段 */
    private Object unwrapResult(Object result) {
        try {
            // 用反射避免硬依赖 Result 类
            Method getData = result.getClass().getMethod("getData");
            Object data = getData.invoke(result);
            return data;
        } catch (Exception e) {
            return result;       // 非 Result 包装，原样返回
        }
    }

    /** 反射调用对象的 getId()，无 id 方法返回 null */
    private Object tryGetId(Object obj) {
        if (obj == null) return null;
        try {
            Method getId = obj.getClass().getMethod("getId");
            return getId.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    /** 把 List<Number> 拼成 "1,2,3,..."，超过 max 个截断 */
    private String joinIds(List<?> list, int max) {
        StringBuilder sb = new StringBuilder();
        int cnt = 0;
        for (Object o : list) {
            if (sb.length() > 0) sb.append(",");
            sb.append(o);
            if (++cnt >= max) { sb.append("..."); break; }
        }
        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /** 取真实客户端 IP（穿透代理） */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
