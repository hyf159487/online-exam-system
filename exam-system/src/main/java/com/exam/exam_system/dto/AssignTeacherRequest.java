package com.exam.exam_system.dto;

import lombok.Data;

import java.util.List;

/**
 * 一键分配任课教师请求参数（管理员，课程列表页用）
 *
 * <p>从全部启用教师（role=1 且 status=1）中随机挑选 minCount~maxCount 名，
 * 绑定到目标课程上。
 */
@Data
public class AssignTeacherRequest {

    /** 目标课程ID列表；为空或 null 表示全部课程 */
    private List<Long> courseIds;

    /** 每门课程分配教师数下限，默认 2 */
    private Integer minCount = 2;

    /** 每门课程分配教师数上限，默认 3 */
    private Integer maxCount = 3;

    /**
     * 是否跳过已有任课教师的课程（默认 true = 只补未绑定的课程）
     * false 表示全部目标课程清空后重新随机分配
     */
    private Boolean skipBound = Boolean.TRUE;
}
