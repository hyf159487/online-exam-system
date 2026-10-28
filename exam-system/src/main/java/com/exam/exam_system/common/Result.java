package com.exam.exam_system.common;

import lombok.Data;

/**
 * 统一响应封装：项目里所有 Controller 都返回这种格式，便于前后端对接
 * 形如：{ "code": 200, "msg": "成功", "data": ... }
 *
 * <p>三种常见状态：
 * <ul>
 *     <li>200  业务成功</li>
 *     <li>4xx  客户端错误（如参数错误、未登录、无权限）</li>
 *     <li>5xx  服务端错误（如代码异常）</li>
 * </ul>
 */
@Data
public class Result<T> {

    private Integer code;
    private String msg;
    private T data;

    /** 业务成功 + 仅消息 */
    public static <T> Result<T> success() {
        return success(null);
    }

    /** 业务成功 + 数据 */
    public static <T> Result<T> success(T data) {
        return success("操作成功", data);
    }

    /** 业务成功 + 自定义消息 + 数据 */
    public static <T> Result<T> success(String msg, T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.msg = msg;
        r.data = data;
        return r;
    }

    /** 通用错误 */
    public static <T> Result<T> error(String msg) {
        return error(500, msg);
    }

    /** 自定义错误码 + 消息 */
    public static <T> Result<T> error(Integer code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        r.data = null;
        return r;
    }

    /** 自定义错误码 + 消息 + 数据（供前端做结构化处理，如"题库不足"自动调整题数） */
    public static <T> Result<T> error(Integer code, String msg, T data) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        r.data = data;
        return r;
    }
}
