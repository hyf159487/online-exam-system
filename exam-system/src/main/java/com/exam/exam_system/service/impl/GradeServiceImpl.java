package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.GradeQuery;
import com.exam.exam_system.dto.GradeStats;
import com.exam.exam_system.dto.PaperQuestionItem;
import com.exam.exam_system.dto.ReviewDetail;
import com.exam.exam_system.dto.ReviewItem;
import com.exam.exam_system.dto.ReviewSubmitRequest;
import com.exam.exam_system.entity.ExamAnswer;
import com.exam.exam_system.entity.ExamPaper;
import com.exam.exam_system.entity.ExamRecord;
import com.exam.exam_system.mapper.ExamAnswerMapper;
import com.exam.exam_system.mapper.ExamPaperMapper;
import com.exam.exam_system.mapper.ExamRecordMapper;
import com.exam.exam_system.mapper.PaperQuestionMapper;
import com.exam.exam_system.service.GradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 成绩管理 Service 实现
 */
@Service
public class GradeServiceImpl implements GradeService {

    @Autowired
    private ExamRecordMapper examRecordMapper;

    @Autowired
    private ExamAnswerMapper examAnswerMapper;

    @Autowired
    private PaperQuestionMapper paperQuestionMapper;

    @Autowired
    private ExamPaperMapper examPaperMapper;

    @Override
    public PageResult<ExamRecord> page(GradeQuery query) {
        Long total = examRecordMapper.countByQuery(query);
        List<ExamRecord> records = examRecordMapper.listByQuery(query);
        return new PageResult<>(records, total == null ? 0L : total,
                query.getPageNum().longValue(), query.getPageSize().longValue());
    }

    @Override
    public GradeStats stats(Long paperId, Long courseId, List<Long> courseIds) {
        return examRecordMapper.stats(paperId, courseId, courseIds);
    }

    @Override
    public Long courseIdOfRecord(Long recordId) {
        ExamRecord record = examRecordMapper.selectById(recordId);
        if (record == null) {
            return null;
        }
        // 成绩挂在 exam_record → exam_paper.course_id 上，取出课程用于数据权限校验
        ExamPaper paper = examPaperMapper.selectById(record.getPaperId());
        return paper == null ? null : paper.getCourseId();
    }

    @Override
    public PageResult<ExamRecord> listPending(GradeQuery query) {
        Long total = examRecordMapper.countPending(query);
        List<ExamRecord> records = examRecordMapper.listPending(query);
        return new PageResult<>(records, total == null ? 0L : total,
                query.getPageNum().longValue(), query.getPageSize().longValue());
    }

    @Override
    public ReviewDetail reviewDetail(Long recordId) {
        ExamRecord record = examRecordMapper.selectById(recordId);
        if (record == null) {
            throw new RuntimeException("考试记录不存在");
        }
        if (record.getStatus() == null || record.getStatus() < 1) {
            throw new RuntimeException("该记录尚未交卷，无法阅卷");
        }
        ExamPaper paper = examPaperMapper.selectById(record.getPaperId());

        // 试卷题目（含题干/选项/标准答案/分值）
        List<PaperQuestionItem> paperQuestions = paperQuestionMapper.listByPaperId(record.getPaperId());
        // 学生作答（按题目ID索引）
        Map<Long, ExamAnswer> answerMap = new HashMap<>();
        for (ExamAnswer a : examAnswerMapper.findByRecordId(recordId)) {
            answerMap.put(a.getQuestionId(), a);
        }

        ReviewDetail detail = new ReviewDetail();
        detail.setRecordId(recordId);
        detail.setPaperId(record.getPaperId());
        detail.setPaperName(paper == null ? "" : paper.getPaperName());
        detail.setCourseName(record.getCourseName());
        detail.setUsername(record.getUsername());
        detail.setRealName(record.getRealName());
        detail.setStartTime(record.getStartTime());
        detail.setSubmitTime(record.getSubmitTime());
        detail.setStatus(record.getStatus());
        detail.setTotalScore(record.getTotalScore() == null ? BigDecimal.ZERO : record.getTotalScore());
        detail.setPaperTotalScore(paper == null || paper.getTotalScore() == null
                ? BigDecimal.ZERO : paper.getTotalScore());

        BigDecimal objectiveScore = BigDecimal.ZERO;
        java.util.ArrayList<ReviewItem> items = new java.util.ArrayList<>();
        for (PaperQuestionItem pq : paperQuestions) {
            ReviewItem item = new ReviewItem();
            item.setQuestionId(pq.getQuestionId());
            item.setQuestionType(pq.getQuestionType());
            item.setContent(pq.getContent());
            item.setOptions(pq.getOptions());
            item.setStandardAnswer(pq.getAnswer());
            item.setScore(pq.getScore());

            ExamAnswer answer = answerMap.get(pq.getQuestionId());
            if (answer != null) {
                // 答题明细ID：前端主观题 AI 预评分需要按 answerId 定位
                item.setAnswerId(answer.getId());
                item.setUserAnswer(answer.getUserAnswer());
                item.setIsCorrect(answer.getIsCorrect());
                item.setGetScore(answer.getGetScore());
                item.setTeacherComment(answer.getTeacherComment());
                item.setAnswered(answer.getUserAnswer() != null && !answer.getUserAnswer().isBlank());
            } else {
                item.setAnswered(false);
            }
            // 客观题（1单选 2多选 3判断 4填空）得分计入客观分
            if (pq.getQuestionType() != null && pq.getQuestionType() <= 4
                    && item.getGetScore() != null) {
                objectiveScore = objectiveScore.add(item.getGetScore());
            }
            items.add(item);
        }
        detail.setObjectiveScore(objectiveScore);
        detail.setQuestions(items);
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReview(Long recordId, ReviewSubmitRequest request) {
        ExamRecord record = examRecordMapper.selectById(recordId);
        if (record == null) {
            throw new RuntimeException("考试记录不存在");
        }
        if (record.getStatus() == null || record.getStatus() != 1) {
            throw new RuntimeException("只有待阅卷记录可以判分");
        }
        // 允许 items 为空：纯客观题试卷没有简答题，直接确认客观分并完结
        // 有简答题时，前端必须为每道简答题打分后再提交
        if (request == null || request.getItems() == null) {
            request = new ReviewSubmitRequest();
            request.setItems(new java.util.ArrayList<>());
        }

        // 试卷题目 → 分值索引（校验教师给分不得超过该题分值）
        Map<Long, BigDecimal> scoreMap = new HashMap<>();
        for (PaperQuestionItem pq : paperQuestionMapper.listByPaperId(record.getPaperId())) {
            scoreMap.put(pq.getQuestionId(), pq.getScore());
        }

        for (ReviewSubmitRequest.Item item : request.getItems()) {
            if (item.getQuestionId() == null || item.getScore() == null) {
                continue;
            }
            BigDecimal max = scoreMap.get(item.getQuestionId());
            if (max == null) {
                throw new RuntimeException("题目不在本试卷中，无法判分");
            }
            if (item.getScore().compareTo(BigDecimal.ZERO) < 0
                    || item.getScore().compareTo(max) > 0) {
                throw new RuntimeException("判分超出范围：题目分值 " + max + "，给分 " + item.getScore());
            }
            ExamAnswer answer = examAnswerMapper.findByRecordAndQuestion(recordId, item.getQuestionId());
            if (answer == null) {
                // 学生该题未作答，也允许教师按空白卷判分（创建一行记录）
                answer = new ExamAnswer();
                answer.setRecordId(recordId);
                answer.setQuestionId(item.getQuestionId());
                answer.setUserAnswer(null);
                examAnswerMapper.insert(answer);
                answer = examAnswerMapper.findByRecordAndQuestion(recordId, item.getQuestionId());
            }
            ExamAnswer update = new ExamAnswer();
            update.setId(answer.getId());
            update.setIsCorrect(item.getScore().compareTo(BigDecimal.ZERO) > 0 ? 1 : 0);
            update.setGetScore(item.getScore());
            update.setTeacherComment(item.getComment());
            examAnswerMapper.updateReview(update);
        }

        // 重新计算总分 = 全部答题得分之和（客观题 + 教师给分）
        BigDecimal total = BigDecimal.ZERO;
        for (ExamAnswer a : examAnswerMapper.findByRecordId(recordId)) {
            if (a.getGetScore() != null) {
                total = total.add(a.getGetScore());
            }
        }

        ExamPaper paper = examPaperMapper.selectById(record.getPaperId());
        ExamRecord updateRecord = new ExamRecord();
        updateRecord.setId(recordId);
        updateRecord.setTotalScore(total);
        updateRecord.setStatus(2); // 已判分
        boolean pass = paper != null && paper.getPassScore() != null
                && total.compareTo(paper.getPassScore()) >= 0;
        updateRecord.setIsPass(pass ? 1 : 0);
        examRecordMapper.updateReviewScore(updateRecord);
    }
}
