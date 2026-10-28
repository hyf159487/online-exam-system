package com.exam.exam_system.dto;

import lombok.Data;

import java.util.List;

/**
 * 教师端错题查询条件：支持按 学生 / 班级 / 学科 / 题型 筛选全班错题
 */
@Data
public class TeacherWrongQuery {

    /** 当前页码，从 1 开始 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 按学生筛选（sys_user.id） */
    private Long studentId;

    /** 按班级筛选（sys_user.class_id） */
    private Long classId;

    /** 按学科筛选 */
    private Long courseId;

    /** 按题型筛选（1单选 2多选 3判断 4填空 5简答） */
    private Integer questionType;

    /**
     * 数据权限：只查这些课程下的错题（由后端按当前登录教师注入，前端不传）。
     * null = 不限制（管理员）；教师没有任何任教课程时为 [-1]，查不到数据
     */
    private List<Long> courseIds;

    /** 计算 LIMIT 偏移量 */
    public int getOffset() {
        int pn = pageNum == null ? 1 : pageNum;
        int ps = pageSize == null ? 10 : pageSize;
        return (pn - 1) * ps;
    }
}
