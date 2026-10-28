package com.exam.exam_system.dto;

import lombok.Data;

import java.util.List;

/**
 * 成绩分页查询参数
 */
@Data
public class GradeQuery {

    private Integer pageNum = 1;
    private Integer pageSize = 10;

    /** 试卷筛选（可空 = 查全部试卷的成绩） */
    private Long paperId;

    /** 学科筛选（可空 = 查全部学科） */
    private Long courseId;

    /** 学号 / 姓名模糊搜索 */
    private String keyword;

    /**
     * 数据权限：只查这些课程下的成绩（由后端按当前登录教师注入，前端不传）。
     * null = 不限制（管理员）
     */
    private List<Long> courseIds;

    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }
}
