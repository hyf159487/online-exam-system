package com.exam.exam_system.dto;

import lombok.Data;

import java.util.List;

/**
 * 高频错题查询参数
 */
@Data
public class HotWrongQuery {

    private Integer pageNum = 1;
    private Integer pageSize = 10;

    /** 题型：1=单选 2=多选 3=判断 4=填空 5=简答 */
    private Integer questionType;

    /** 难度：1=简单 2=中等 3=困难 */
    private Integer difficulty;

    /** 所属课程 */
    private Long courseId;

    /**
     * 数据权限：只统计这些课程下的错题（由后端按当前登录教师注入，前端不传）。
     * null = 不限制（管理员）
     */
    private List<Long> courseIds;

    /** 最低错误率筛选（百分比，如 30 表示 30%） */
    private Double minErrorRate;

    /** 排序规则：errorRate_desc(默认) / totalCount_desc / wrongCount_desc */
    private String sortBy = "errorRate_desc";

    public int getOffset() {
        int pn = pageNum == null ? 1 : pageNum;
        int ps = pageSize == null ? 10 : pageSize;
        return Math.max(0, (pn - 1) * ps);
    }
}
