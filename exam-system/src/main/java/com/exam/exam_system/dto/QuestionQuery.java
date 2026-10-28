package com.exam.exam_system.dto;

import lombok.Data;

import java.util.List;

/**
 * 题库查询参数
 *
 * <p>前端查询时把查询条件按字段传进来即可，未传的字段为 null，SQL 自动忽略。
 * 通过 {@link #getOffset()} 计算分页偏移量。
 */
@Data
public class QuestionQuery {

    /** 第几页（从 1 开始），默认 1 */
    private Integer pageNum = 1;

    /** 每页多少条，默认 10 */
    private Integer pageSize = 10;

    /** 按题目ID精确查询（可选，审核历史跳转题库定位用） */
    private Long id;

    /** 按题型筛（可选）：1=单选 2=多选 3=判断 4=填空 5=简答 */
    private Integer questionType;

    /** 按难度筛（可选）：1=简单 2=中等 3=困难 */
    private Integer difficulty;

    /** 按课程筛（可选） */
    private Long courseId;

    /** 按审核状态筛（可选）：0待审核 1通过 2驳回 */
    private Integer auditStatus;

    /** 按提交人筛（可选，教师看自己的） */
    private Long creatorId;

    /**
     * 数据权限用：只查这些课程下的题目（由后端按当前登录教师注入，前端不传）。
     *
     * <p>null = 不限制（管理员）；非 null = 只允许这些课程，
     * 教师没有任何任教课程时后端会注入 [-1]，SQL 天然查不到数据。
     */
    private List<Long> courseIds;

    /** 题干关键词模糊搜索（可选） */
    private String keyword;

    /** 按审核人筛（可选，审核历史用） */
    private Long auditUserId;

    /** 提交时间范围筛选：开始时间 */
    private String startTime;

    /** 提交时间范围筛选：结束时间 */
    private String endTime;

    /** 排序规则，格式 "字段_方向"，如 "createTime_desc" / "course_asc" / "score_desc" */
    private String sortBy = "createTime_desc";

    /** MyBatis 动态 SQL 用：分页偏移 = (页码-1) * 每页条数 */
    public int getOffset() {
        int pn = pageNum == null ? 1 : pageNum;
        int ps = pageSize == null ? 10 : pageSize;
        return Math.max(0, (pn - 1) * ps);
    }
}
