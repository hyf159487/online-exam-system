package com.exam.exam_system.dto;

import lombok.Data;

/**
 * 班级学情统计查询参数
 */
@Data
public class ClassStatsQuery {

    /** 考试 ID */
    private Long examId;

    /** 班级 ID */
    private Long classId;

    /** 班级名称（联表回显） */
    private String className;

    /** 学生 ID（作答详情接口用） */
    private Long studentId;

    /** 班级总人数（列表下拉回显：班级一共多少学生，用于对照缺考） */
    private Integer classSize;

    /** 教师 ID（权限过滤用） */
    private Long teacherId;

    /** 角色：1=教师 2=管理员 */
    private Integer role;

    /** 分页 */
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
