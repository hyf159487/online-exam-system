package com.exam.exam_system.dto;

import lombok.Data;

import java.util.List;

/**
 * 考试分页查询参数
 *
 * <p>runtimeStatus 是推导状态的筛选值：
 * 0=未开始 1=进行中 2=已结束 3=已撤回（null 查全部）
 */
@Data
public class ExamQuery {

    private Integer pageNum = 1;
    private Integer pageSize = 10;

    /** 考试名称模糊搜索 */
    private String keyword;

    /** 运行状态筛选（可空） */
    private Integer runtimeStatus;

    /** 学科筛选（可空） */
    private Long courseId;

    /**
     * 数据权限：只查这些课程下的考试（由后端按当前登录教师注入，前端不传）。
     * null = 不限制（管理员）
     */
    private List<Long> courseIds;

    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }
}
