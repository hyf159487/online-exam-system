package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.ReviewWaitVO;
import com.exam.exam_system.dto.SaveScoreRequest;
import com.exam.exam_system.entity.Exam;
import com.exam.exam_system.entity.ExamAnswer;
import com.exam.exam_system.entity.ExamRecord;
import com.exam.exam_system.mapper.ExamMapper;
import com.exam.exam_system.mapper.ExamRecordMapper;
import com.exam.exam_system.mapper.ExamReviewMapper;
import com.exam.exam_system.service.ExamReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 主观题阅卷 Service 实现
 */
@Service
public class ExamReviewServiceImpl implements ExamReviewService {

    @Autowired
    private ExamReviewMapper reviewMapper;

    @Autowired
    private ExamRecordMapper recordMapper;

    @Autowired
    private ExamMapper examMapper;

    @Override
    public PageResult<ReviewWaitVO> listSubjectiveAnswers(Long examId, Long classId, Integer reviewStatus,
                                                            String keyword, Long creatorId, Integer role,
                                                            Long pageNum, Long pageSize) {
        long total = reviewMapper.countSubjectiveAnswers(examId, classId, reviewStatus, keyword, creatorId, role);
        List<ReviewWaitVO> rows = new ArrayList<>();
        if (total > 0) {
            int pn = pageNum.intValue();
            int ps = pageSize.intValue();
            int offset = (pn - 1) * ps;
            rows = reviewMapper.listSubjectiveAnswers(examId, classId, reviewStatus, keyword, creatorId, role, offset, ps);
        }
        return new PageResult<>(rows, total, pageNum, pageSize);
    }

    @Override
    public ExamAnswer getAnswerDetail(Long answerId, Long operatorId, Integer operatorRole) {
        ExamAnswer answer = reviewMapper.findDetailById(answerId);
        if (answer == null) {
            throw new RuntimeException("答题记录不存在");
        }
        // 权限校验：教师只能查看自己考试的答题
        if (operatorRole != null && operatorRole == 1) {
            Exam exam = examMapper.selectById(answer.getExamId());
            if (exam != null && !exam.getCreatorId().equals(operatorId)) {
                throw new RuntimeException("无权查看该答题记录");
            }
        }
        return answer;
    }

    @Override
    @Transactional
    public void saveScore(SaveScoreRequest req, Long operatorId, Integer operatorRole) {
        if (req.getAnswerId() == null) {
            throw new RuntimeException("答题ID不能为空");
        }
        if (req.getReviewScore() == null) {
            throw new RuntimeException("得分不能为空");
        }

        // 1. 查询答题详情
        ExamAnswer answer = reviewMapper.findDetailById(req.getAnswerId());
        if (answer == null) {
            throw new RuntimeException("答题记录不存在");
        }

        // 2. 权限校验
        if (operatorRole != null && operatorRole == 1) {
            Exam exam = examMapper.selectById(answer.getExamId());
            if (exam != null && !exam.getCreatorId().equals(operatorId)) {
                throw new RuntimeException("无权批阅该答题");
            }
        }

        // 3. 校验得分不超过试题满分
        if (answer.getQuestionScore() != null && req.getReviewScore().compareTo(answer.getQuestionScore()) > 0) {
            throw new RuntimeException("得分不能超过试题满分 " + answer.getQuestionScore());
        }
        if (req.getReviewScore().compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("得分不能为负数");
        }

        // 4. 更新阅卷信息
        reviewMapper.updateReviewScore(
                answer.getId(),
                req.getReviewScore(),
                req.getReviewOpinion(),
                operatorId,
                LocalDateTime.now()
        );

        // 5. 重新计算学生总成绩
        ExamRecord record = recordMapper.selectById(answer.getRecordId());
        if (record == null) {
            throw new RuntimeException("考试记录不存在");
        }

        BigDecimal objectiveScore = reviewMapper.sumObjectiveScore(record.getId());
        BigDecimal subjectiveScore = reviewMapper.sumReviewedSubjectiveScore(record.getId());
        BigDecimal totalScore = objectiveScore.add(subjectiveScore);

        // 6. 检查是否还有未批阅的主观题
        int pendingCount = reviewMapper.countPendingSubjective(record.getId());

        // 7. 更新考试记录
        Exam exam = examMapper.selectById(record.getExamId());
        boolean isPass = exam != null && totalScore.compareTo(exam.getPassScore()) >= 0;

        // 如果全部主观题已批阅，标记为已判分（status=2）；否则保持已交卷（status=1）
        int newStatus = pendingCount == 0 ? 2 : 1;

        record.setTotalScore(totalScore);
        record.setStatus(newStatus);
        record.setIsPass(isPass ? 1 : 0);
        recordMapper.updateReviewScore(record);
    }
}
