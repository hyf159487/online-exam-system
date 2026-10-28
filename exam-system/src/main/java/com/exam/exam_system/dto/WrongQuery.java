package com.exam.exam_system.dto;

import lombok.Data;

/**
 * 错题本分页查询条件（学生端）
 *
 * <p>支持按学科、题型、错误次数区间组合筛选；导出接口复用同一套条件（只忽略分页）。
 */
@Data
public class WrongQuery {

    /** 当前页码，从 1 开始 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 按学科筛选（可空 = 全部学科） */
    private Long courseId;

    /** 按题型筛选：1=单选 2=多选 3=判断 4=填空 5=简答 6=名词解释（可空 = 全部题型） */
    private Integer questionType;

    /** 错误次数下限（含，可空）：用于「错 2 次以上」这类筛选 */
    private Integer wrongCountMin;

    /** 错误次数上限（含，可空）：与下限相同即「正好错 N 次」 */
    private Integer wrongCountMax;

    /** 计算 LIMIT 偏移量 */
    public int getOffset() {
        int pn = pageNum == null ? 1 : pageNum;
        int ps = pageSize == null ? 10 : pageSize;
        return (pn - 1) * ps;
    }
}
