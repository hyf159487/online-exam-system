package com.exam.exam_system.service.impl;

import com.exam.exam_system.dto.QuestionQualityVO;
import com.exam.exam_system.dto.StatsExamVO;
import com.exam.exam_system.mapper.StatsQuestionMapper;
import com.exam.exam_system.service.StatsQuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 试题质量统计 Service 实现
 */
@Service
public class StatsQuestionServiceImpl implements StatsQuestionService {

    @Autowired
    private StatsQuestionMapper statsMapper;

    @Override
    public List<StatsExamVO> getExamList(Long teacherId, Integer role) {
        return statsMapper.listExams(teacherId, role);
    }

    @Override
    public List<QuestionQualityVO> getQuestionQualityList(Long examId) {
        List<StatsQuestionMapper.QuestionBasicInfo> questions = statsMapper.listQuestionsByExam(examId);
        List<QuestionQualityVO> result = new ArrayList<>();

        for (StatsQuestionMapper.QuestionBasicInfo q : questions) {
            QuestionQualityVO vo = new QuestionQualityVO();
            vo.setQuestionId(q.getId());
            vo.setContent(q.getContent());
            vo.setFullContent(q.getContent());
            vo.setQuestionType(q.getQuestionType());
            vo.setQuestionTypeName(getTypeName(q.getQuestionType()));
            vo.setOptions(q.getOptions());
            vo.setAnswer(q.getAnswer());
            vo.setAnalysis(q.getAnalysis());
            vo.setScore(q.getScore() != null ? q.getScore() : BigDecimal.ZERO);

            // 统计指标
            int total = statsMapper.countTotalAnswer(examId, q.getId());
            int correct = statsMapper.countCorrectAnswer(examId, q.getId());
            int wrong = total - correct;
            BigDecimal avgScore = statsMapper.avgScore(examId, q.getId());

            vo.setTotalCount(total);
            vo.setCorrectCount(correct);
            vo.setWrongCount(wrong);
            vo.setCorrectRate(total > 0
                    ? new BigDecimal(correct * 100.0 / total).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            vo.setWrongRate(total > 0
                    ? new BigDecimal(wrong * 100.0 / total).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);

            // 难度系数 P = 平均分 / 满分
            BigDecimal fullScore = vo.getScore();
            if (fullScore != null && fullScore.compareTo(BigDecimal.ZERO) > 0 && total > 0) {
                BigDecimal difficulty = avgScore.divide(fullScore, 4, RoundingMode.HALF_UP);
                vo.setDifficulty(difficulty);
                vo.setDifficultyLevel(getDifficultyLevel(difficulty));
            } else {
                vo.setDifficulty(BigDecimal.ZERO);
                vo.setDifficultyLevel("无数据");
            }

            result.add(vo);
        }
        return result;
    }

    @Override
    public QuestionQualityVO getAnswerDetail(Long examId, Long questionId) {
        List<QuestionQualityVO.AnswerDetailItem> details = statsMapper.listAnswerDetails(examId, questionId);
        QuestionQualityVO vo = new QuestionQualityVO();
        vo.setAnswerDetails(details);
        return vo;
    }

    private String getTypeName(Integer type) {
        if (type == null) return "未知";
        switch (type) {
            case 1: return "单选题";
            case 2: return "多选题";
            case 3: return "判断题";
            case 4: return "填空题";
            case 5: return "简答题";
            case 6: return "名词解释";
            default: return "未知";
        }
    }

    private String getDifficultyLevel(BigDecimal p) {
        if (p == null) return "无数据";
        if (p.compareTo(new BigDecimal("0.2")) < 0) return "过难";
        if (p.compareTo(new BigDecimal("0.4")) < 0) return "偏难";
        if (p.compareTo(new BigDecimal("0.7")) < 0) return "适中";
        if (p.compareTo(new BigDecimal("0.9")) < 0) return "偏易";
        return "过易";
    }
}
