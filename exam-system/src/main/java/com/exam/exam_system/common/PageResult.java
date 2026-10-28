package com.exam.exam_system.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果封装
 *
 * <p>前端收到这个对象，就能渲染表格 + 分页器了：
 * <pre>
 * {
 *   "records":  [ {...}, {...}, ... ],   // 当前页数据
 *   "total":    152,                    // 总记录数
 *   "pageNum":  1,                      // 当前页码
 *   "pageSize": 10                      // 每页大小
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> records;

    /** 总记录数 */
    private Long total;

    /** 当前页码（从 1 开始） */
    private Long pageNum;

    /** 每页大小 */
    private Long pageSize;

    /** 工厂方法：把一页数据和元信息组装成 PageResult */
    public static <T> PageResult<T> of(List<T> records, Long total, Long pageNum, Long pageSize) {
        return new PageResult<>(records, total, pageNum, pageSize);
    }
}