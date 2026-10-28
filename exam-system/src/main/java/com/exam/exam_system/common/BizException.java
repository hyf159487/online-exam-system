package com.exam.exam_system.common;

import lombok.Getter;

/**
 * 携带附加数据的业务异常。
 *
 * <p>当业务失败时需要给前端提供结构化数据（如随机组卷"题库不足"时的可用题量）时抛出，
 * 由 {@link GlobalExceptionHandler} 转成 {code, msg, data} 统一响应，
 * 前端可据此弹选择框做后续操作（自动调整题数 / 前往添加题目）。
 */
@Getter
public class BizException extends RuntimeException {

    private final Object data;

    public BizException(String message) {
        this(message, null);
    }

    public BizException(String message, Object data) {
        super(message);
        this.data = data;
    }
}
