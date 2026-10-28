package com.exam.exam_system.common;

/**
 * 数据越权异常：登录身份合法（token 有效、角色也够），
 * 但试图操作「不属于自己」的数据时抛出。
 *
 * <p>典型场景：教师 A 想查看/修改教师 B 任教课程下的试题、试卷、考试、成绩。
 *
 * <p>由 {@link GlobalExceptionHandler} 统一转成 <code>code=403</code> 的响应，
 * 前端拦截器会直接弹出 msg 提示。
 *
 * <p>与 {@link BizException}（code=400，业务规则不满足）区分开：
 * 403 表示「你没这个数据权限」，400 表示「操作本身不合法」。
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    /** 默认提示语 */
    public static ForbiddenException of(String message) {
        return new ForbiddenException(message);
    }
}
