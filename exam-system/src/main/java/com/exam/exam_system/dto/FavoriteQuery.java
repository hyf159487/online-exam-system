package com.exam.exam_system.dto;

import lombok.Data;

/**
 * 我的收藏分页查询条件（学生端）
 *
 * <p>支持按学科、题型、题干关键词组合筛选；导出接口复用同一套条件（只忽略分页）。
 */
@Data
public class FavoriteQuery {

    /** 当前页码，从 1 开始 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 按学科筛选（可空 = 全部学科） */
    private Long courseId;

    /** 按题型筛选：1=单选 2=多选 3=判断 4=填空 5=简答 6=名词解释（可空 = 全部题型） */
    private Integer questionType;

    /** 题干关键词（可空 = 不筛选；Service 会把空白串归一成 null） */
    private String keyword;

    /** 计算 LIMIT 偏移量 */
    public int getOffset() {
        int pn = pageNum == null ? 1 : pageNum;
        int ps = pageSize == null ? 10 : pageSize;
        return (pn - 1) * ps;
    }
}
