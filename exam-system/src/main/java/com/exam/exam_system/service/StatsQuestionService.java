package com.exam.exam_system.service;

import com.exam.exam_system.dto.QuestionQualityVO;
import com.exam.exam_system.dto.StatsExamVO;

import java.util.List;

/**
 * 试题质量统计 Service
 */
public interface StatsQuestionService {

    /**
     * 获取可统计的考试列表
     */
    List<StatsExamVO> getExamList(Long teacherId, Integer role);

    /**
     * 获取某考试下所有试题的质量指标
     */
    List<QuestionQualityVO> getQuestionQualityList(Long examId);

    /**
     * 获取某题目的学生答题明细
     */
    QuestionQualityVO getAnswerDetail(Long examId, Long questionId);
}
