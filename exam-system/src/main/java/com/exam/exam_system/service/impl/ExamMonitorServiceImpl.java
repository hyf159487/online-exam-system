package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.MonitorStudentVO;
import com.exam.exam_system.dto.PaperQuestionItem;
import com.exam.exam_system.dto.RunningExamVO;
import com.exam.exam_system.dto.StudentAnswerDetailVO;
import com.exam.exam_system.entity.Exam;
import com.exam.exam_system.entity.ExamAnswer;
import com.exam.exam_system.entity.ExamRecord;
import com.exam.exam_system.entity.ExamStudent;
import com.exam.exam_system.entity.WrongQuestion;
import com.exam.exam_system.mapper.ExamAnswerMapper;
import com.exam.exam_system.mapper.ExamMapper;
import com.exam.exam_system.mapper.ExamMonitorMapper;
import com.exam.exam_system.mapper.ExamRecordMapper;
import com.exam.exam_system.mapper.PaperQuestionMapper;
import com.exam.exam_system.service.ExamMonitorService;
import com.exam.exam_system.service.WrongQuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 考试监控 Service 实现
 */
@Service
public class ExamMonitorServiceImpl implements ExamMonitorService {

    @Autowired
    private ExamMonitorMapper monitorMapper;

    @Autowired
    private ExamMapper examMapper;

    @Autowired
    private ExamRecordMapper recordMapper;

    @Autowired
    private ExamAnswerMapper answerMapper;

    @Autowired
    private PaperQuestionMapper paperQuestionMapper;

    @Autowired
    private WrongQuestionService wrongQuestionService;

    @Override
    public List<RunningExamVO> listRunningExams(List<Long> courseIds) {
        return monitorMapper.listRunningExams(courseIds);
    }

    @Override
    public PageResult<MonitorStudentVO> listStudents(Long examId, Long classId, Integer status,
                                                      String keyword, Long pageNum, Long pageSize) {
        long total = monitorMapper.countStudents(examId, classId, status, keyword);
        List<MonitorStudentVO> rows = new ArrayList<>();
        if (total > 0) {
            int pn = pageNum.intValue();
            int ps = pageSize.intValue();
            int offset = (pn - 1) * ps;
            rows = monitorMapper.listStudents(examId, classId, status, keyword, offset, ps);
        }
        return new PageResult<>(rows, total, pageNum, pageSize);
    }

    @Override
    public List<StudentAnswerDetailVO> listStudentAnswers(Long examId, Long studentId) {
        return monitorMapper.listStudentAnswers(examId, studentId);
    }

    @Override
    @Transactional
    public void forceSubmit(Long examId, Long studentId, Long operatorId, Integer operatorRole) {
        // 1. 校验考试
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new RuntimeException("考试不存在");
        }

        // 2. 权限校验：教师只能操作自己的考试
        if (operatorRole != null && operatorRole == 1) {
            if (!exam.getCreatorId().equals(operatorId)) {
                throw new RuntimeException("无权操作该考试");
            }
        }

        // 3. 查 exam_student 记录
        ExamStudent es = monitorMapper.findByExamAndStudent(examId, studentId);
        if (es == null) {
            throw new RuntimeException("未找到该学生的考试记录");
        }
        if (es.getStatus() != null && es.getStatus() >= 2) {
            throw new RuntimeException("该学生已交卷，无需强制交卷");
        }

        // 4. 拉取试卷全部题目
        List<PaperQuestionItem> items = paperQuestionMapper.listByPaperId(exam.getPaperId());
        Map<Long, PaperQuestionItem> questionMap = items.stream()
                .collect(Collectors.toMap(PaperQuestionItem::getQuestionId, Function.identity()));

        // 5. 获取学生现有作答（exam_answer 表中已保存的答案）
        ExamRecord record = recordMapper.findOngoingByPaperAndUser(exam.getPaperId(), studentId);
        if (record == null) {
            // 学生可能还未创建 exam_record（但有 exam_student 记录），创建一条
            record = new ExamRecord();
            record.setPaperId(exam.getPaperId());
            record.setUserId(studentId);
            record.setStartTime(es.getEnterTime() != null ? es.getEnterTime() : LocalDateTime.now());
            record.setExamId(examId);
            record.setStatus(0);
            recordMapper.insert(record);
        }

        List<ExamAnswer> existingAnswers = answerMapper.findByRecordId(record.getId());
        Map<Long, String> answerMap = existingAnswers.stream()
                .collect(Collectors.toMap(ExamAnswer::getQuestionId, a ->
                        a.getUserAnswer() == null ? "" : a.getUserAnswer(), (a, b) -> b));

        // 6. 逐题判分
        BigDecimal total = BigDecimal.ZERO;
        int subjective = 0;
        List<ExamAnswer> answerRows = new ArrayList<>();
        List<WrongQuestion> wrongList = new ArrayList<>();

        for (PaperQuestionItem q : items) {
            ExamAnswer row = new ExamAnswer();
            row.setQuestionId(q.getQuestionId());
            String userAns = answerMap.getOrDefault(q.getQuestionId(), "");
            row.setUserAnswer(userAns);

            if (q.getQuestionType() != null && (q.getQuestionType() == 5 || q.getQuestionType() == 6)) {
                subjective++;
                row.setIsCorrect(null);
                row.setGetScore(BigDecimal.ZERO);
            } else {
                boolean correct = judge(q.getQuestionType(), userAns, q.getAnswer());
                row.setIsCorrect(correct ? 1 : 0);
                BigDecimal got = correct ? q.getScore() : BigDecimal.ZERO;
                row.setGetScore(got);
                total = total.add(got);
                if (!correct) {
                    WrongQuestion wq = new WrongQuestion();
                    wq.setUserId(studentId);
                    wq.setExamId(examId);
                    wq.setPaperId(exam.getPaperId());
                    wq.setQuestionId(q.getQuestionId());
                    wq.setCourseId(q.getCourseId() != null ? q.getCourseId() : exam.getCourseId());
                    wq.setQuestionType(q.getQuestionType());
                    wq.setUserAnswer(userAns);
                    wq.setCorrectAnswer(q.getAnswer());
                    wq.setScore(q.getScore());
                    wrongList.add(wq);
                }
            }
            answerRows.add(row);
        }

        // 7. 更新考试记录（exam_record）
        LocalDateTime now = LocalDateTime.now();
        boolean hasSubjective = subjective > 0;
        record.setSubmitTime(now);
        record.setTotalScore(total);
        record.setStatus(hasSubjective ? 1 : 2);
        record.setIsPass(total.compareTo(exam.getPassScore()) >= 0 ? 1 : 0);
        // 成绩快照：冗余试卷名/满分/及格分，防止考试/试卷被删除后成绩信息丢失
        record.setPaperName(exam.getPaperName());
        record.setPaperTotalScore(exam.getTotalScore());
        record.setPassScore(exam.getPassScore());
        recordMapper.updateSubmit(record);

        // 8. 写答题明细（清除旧记录后重写）
        answerMapper.deleteByRecordId(record.getId());
        for (ExamAnswer row : answerRows) {
            row.setRecordId(record.getId());
            answerMapper.insert(row);
        }

        // 9. 归集错题
        for (WrongQuestion wq : wrongList) {
            wq.setRecordId(record.getId());
            wrongQuestionService.collect(wq);
        }

        // 10. 更新 exam_student 状态为"强制交卷"
        monitorMapper.updateStatus(es.getId(), 3);
    }

    /**
     * 客观题判分
     */
    private boolean judge(Integer questionType, String userAnswer, String standardAnswer) {
        if (standardAnswer == null || userAnswer == null) return false;
        String u = userAnswer.trim();
        String s = standardAnswer.trim();
        if (u.isEmpty()) return false;

        if (questionType != null && questionType == 2) {
            return normalizeMulti(u).equals(normalizeMulti(s));
        }
        return u.equals(s);
    }

    private String normalizeMulti(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        return java.util.Arrays.stream(raw.split("[,，]"))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .sorted()
                .collect(Collectors.joining(","));
    }
}
