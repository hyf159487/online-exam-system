package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.ReviewWaitVO;
import com.exam.exam_system.dto.SaveScoreRequest;
import com.exam.exam_system.entity.ExamAnswer;

/**
 * 主观题阅卷 Service
 */
public interface ExamReviewService {

    /**
     * 分页查询主观题阅卷列表
     */
    PageResult<ReviewWaitVO> listSubjectiveAnswers(Long examId, Long classId, Integer reviewStatus,
                                                     String keyword, Long creatorId, Integer role,
                                                     Long pageNum, Long pageSize);

    /**
     * 查询单条答题详情
     */
    ExamAnswer getAnswerDetail(Long answerId, Long operatorId, Integer operatorRole);

    /**
     * 保存阅卷得分和评语，更新总成绩
     */
    void saveScore(SaveScoreRequest req, Long operatorId, Integer operatorRole);
}
