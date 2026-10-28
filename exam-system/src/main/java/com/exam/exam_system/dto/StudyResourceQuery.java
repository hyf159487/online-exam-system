package com.exam.exam_system.dto;

import lombok.Data;

import java.util.List;

/**
 * 学习资料查询参数
 *
 * <p>前端查询时把查询条件按字段传进来即可，未传的字段为 null，SQL 自动忽略。
 * 管理端通常先选课程再加载该课程资料（courseId 必传），支持名称模糊搜索。
 */
@Data
public class StudyResourceQuery {

    /** 第几页（从 1 开始），默认 1 */
    private Integer pageNum = 1;

    /** 每页多少条，默认 10 */
    private Integer pageSize = 10;

    /** 按课程筛（可选，管理端建议必传） */
    private Long courseId;

    /** 资料名称关键词模糊搜索（可选） */
    private String keyword;

    /**
     * 数据权限：教师任教课程 id 列表。
     *
     * <p>由 Controller 调用 {@code TeacherScope.myCourseIds(request)} 注入：
     * null = 管理员不限制；非 null 则 SQL 拼 {@code AND r.course_id IN (...)}。
     */
    private List<Long> courseIds;

    /** MyBatis 动态 SQL 用：分页偏移 = (页码-1) * 每页条数 */
    public int getOffset() {
        int pn = pageNum == null ? 1 : pageNum;
        int ps = pageSize == null ? 10 : pageSize;
        return Math.max(0, (pn - 1) * ps);
    }
}
