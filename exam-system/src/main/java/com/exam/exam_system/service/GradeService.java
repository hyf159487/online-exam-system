package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.GradeQuery;
import com.exam.exam_system.dto.GradeStats;
import com.exam.exam_system.dto.ReviewDetail;
import com.exam.exam_system.dto.ReviewSubmitRequest;
import com.exam.exam_system.entity.ExamRecord;

import java.util.List;

/**
 * 成绩管理 Service
 */
public interface GradeService {

    /** 分页查询成绩 */
    PageResult<ExamRecord> page(GradeQuery query);

    /**
     * 成绩统计（人数/平均/最高/最低/及格数/分数段分布）
     *
     * @param courseIds 数据权限：教师任教课程集合；null = 不限制（管理员）
     */
    GradeStats stats(Long paperId, Long courseId, List<Long> courseIds);

    /** 分页查询待阅卷记录（status=1 已交卷、简答题待教师判分） */
    PageResult<ExamRecord> listPending(GradeQuery query);

    /** 阅卷详情：记录信息 + 学生信息 + 逐题（题干/标准答案/学生作答/得分） */
    ReviewDetail reviewDetail(Long recordId);

    /** 教师提交判分：写入简答题得分与评语，重新计算总分，记录置为已判分 */
    void submitReview(Long recordId, ReviewSubmitRequest request);

    /**
     * 查某条考试记录所属的课程 id（阅卷接口的数据权限校验用）。
     *
     * @return 课程 id；记录不存在时返回 null
     */
    Long courseIdOfRecord(Long recordId);
}
