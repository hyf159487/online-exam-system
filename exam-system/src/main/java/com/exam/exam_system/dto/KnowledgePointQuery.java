package com.exam.exam_system.dto;

import lombok.Data;

import java.util.List;

/**
 * 知识点分页查询参数
 *
 * <p>管理端通常先选课程再加载该课程全部知识点（courseId 建议必传），
 * 支持知识点名称 / 编码关键词模糊搜索。
 */
@Data
public class KnowledgePointQuery {

    /** 第几页（从 1 开始），默认 1 */
    private Integer pageNum = 1;

    /** 每页多少条，默认 10 */
    private Integer pageSize = 10;

    /** 按课程筛（可选，管理端建议必传） */
    private Long courseId;

    /** 知识点名称 / 编码关键词模糊搜索（可选） */
    private String keyword;

    /**
     * 数据权限：只查这些课程下的知识点（由后端按当前登录教师注入，前端不传）。
     * null = 不限制（管理员）
     */
    private List<Long> courseIds;

    /** MyBatis 动态 SQL 用：分页偏移 = (页码-1) * 每页条数 */
    public int getOffset() {
        int pn = pageNum == null ? 1 : pageNum;
        int ps = pageSize == null ? 10 : pageSize;
        return Math.max(0, (pn - 1) * ps);
    }
}
