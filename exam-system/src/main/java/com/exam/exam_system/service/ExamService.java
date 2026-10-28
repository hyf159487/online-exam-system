package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.ExamQuery;
import com.exam.exam_system.entity.Exam;

import java.util.List;

/**
 * 考试 Service
 */
public interface ExamService {

    /** 分页查询（带名称模糊 + 运行状态筛选），并填充 runtimeStatus */
    PageResult<Exam> page(ExamQuery query);

    /** 按 ID 查单个（填充 runtimeStatus） */
    Exam getById(Long id);

    /** 发布考试：校验时间窗口，从试卷快照总分/及格分/时长/学科 */
    void add(Exam exam, Long creatorId);

    /**
     * 按试卷 id 解析出该试卷所属的课程 id。
     *
     * <p>发布/编辑考试的表单里没有「学科」这一项：考试的学科 = 所选试卷的学科，
     * Controller 在做教师数据权限校验前，需要先用试卷换出 courseId。
     *
     * @return 试卷不存在或试卷没有课程归属时返回 null
     */
    Long resolveCourseIdByPaperId(Long paperId);

    /** 更新考试基本信息（重新校验 + 重新快照试卷） */
    void update(Exam exam);

    /** 发布 / 撤回（status: 1=已发布 0=已撤回） */
    void updateStatus(Long id, Integer status);

    /** 提前结束进行中的考试：结束时间改为当前时刻（仅已发布且进行中的考试可操作） */
    void finishNow(Long id);

    /** 重新发布已结束的考试：days 模式（结束 = 当前 + N 天）或自定义时间窗口模式（二选一） */
    void republish(Long id, Integer days, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime);

    /** 删除考试 */
    void delete(Long id);

    /**
     * 考试总数（仪表盘）
     *
     * @param courseIds 数据权限：教师任教课程集合；null = 全站统计（管理员）
     */
    long countAll(List<Long> courseIds);
}
