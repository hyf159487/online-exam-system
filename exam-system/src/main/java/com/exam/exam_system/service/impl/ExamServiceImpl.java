package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.ExamQuery;
import com.exam.exam_system.entity.Exam;
import com.exam.exam_system.entity.ExamPaper;
import com.exam.exam_system.entity.SysUser;
import com.exam.exam_system.mapper.ExamAnswerMapper;
import com.exam.exam_system.mapper.ExamMapper;
import com.exam.exam_system.mapper.ExamMonitorMapper;
import com.exam.exam_system.mapper.ExamPaperMapper;
import com.exam.exam_system.mapper.ExamRecordMapper;
import com.exam.exam_system.mapper.PaperQuestionMapper;
import com.exam.exam_system.mapper.SysUserMapper;
import com.exam.exam_system.service.ExamService;
import com.exam.exam_system.service.NoticeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试 Service 实现
 *
 * <p>核心逻辑：
 * <ul>
 *     <li>发布/更新考试时校验时间窗口，并从试卷快照 totalScore/passScore/duration/courseId</li>
 *     <li>查询结果填充 runtimeStatus（0未开始 1进行中 2已结束 3已撤回）</li>
 * </ul>
 */
@Service
public class ExamServiceImpl implements ExamService {

    private static final Logger log = LoggerFactory.getLogger(ExamServiceImpl.class);

    @Autowired
    private ExamMapper examMapper;

    @Autowired
    private ExamPaperMapper examPaperMapper;

    @Autowired
    private PaperQuestionMapper paperQuestionMapper;

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private ExamRecordMapper examRecordMapper;

    @Autowired
    private ExamAnswerMapper examAnswerMapper;

    @Autowired
    private ExamMonitorMapper examMonitorMapper;

    @Override
    public PageResult<Exam> page(ExamQuery query) {
        long total = examMapper.countByQuery(query);
        List<Exam> records = examMapper.listByQuery(query);
        records.forEach(this::fillRuntimeStatus);
        return PageResult.of(records, total, (long) query.getPageNum(), (long) query.getPageSize());
    }

    @Override
    public Exam getById(Long id) {
        Exam exam = examMapper.selectById(id);
        if (exam != null) {
            fillRuntimeStatus(exam);
        }
        return exam;
    }

    @Override
    public void add(Exam exam, Long creatorId) {
        validateAndSnapshot(exam);
        exam.setCreatorId(creatorId);
        exam.setStatus(1); // 新发布默认已发布
        examMapper.insert(exam);
        // 发布考试后自动通知全体学生；通知失败不影响考试发布主流程
        try {
            String publisherName = null;
            SysUser publisher = sysUserMapper.selectById(creatorId);
            if (publisher != null) {
                publisherName = (publisher.getRealName() != null && !publisher.getRealName().isEmpty())
                        ? publisher.getRealName() : publisher.getUsername();
            }
            noticeService.sendExamNotice(exam, creatorId, publisherName);
        } catch (Exception e) {
            log.error("发布考试自动通知失败，examId={}", exam.getId(), e);
        }
    }

    @Override
    public Long resolveCourseIdByPaperId(Long paperId) {
        if (paperId == null) {
            return null;
        }
        ExamPaper paper = examPaperMapper.selectById(paperId);
        return paper == null ? null : paper.getCourseId();
    }

    @Override
    public void update(Exam exam) {
        if (examMapper.selectById(exam.getId()) == null) {
            throw new IllegalArgumentException("考试不存在");
        }
        validateAndSnapshot(exam);
        examMapper.updateById(exam);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("status 只能是 0（撤回）或 1（发布）");
        }
        examMapper.updateStatus(id, status);
    }

    @Override
    public void finishNow(Long id) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            throw new IllegalArgumentException("考试不存在");
        }
        if (exam.getStatus() == null || exam.getStatus() != 1) {
            throw new IllegalArgumentException("仅已发布的考试可以提前结束");
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
            throw new IllegalArgumentException("考试尚未开始，不能提前结束");
        }
        if (exam.getEndTime() != null && !now.isBefore(exam.getEndTime())) {
            throw new IllegalArgumentException("考试已经结束，无需重复操作");
        }
        // 结束时间改为当前时刻：运行态立即变为"已结束"，未交卷学生按各端剩余时间逻辑收卷
        examMapper.updateEndTime(id, now);
    }

    @Override
    public void republish(Long id, Integer days, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            throw new IllegalArgumentException("考试不存在");
        }
        if (exam.getStatus() == null || exam.getStatus() != 1) {
            throw new IllegalArgumentException("仅已发布状态的考试可以重新发布，已撤回的考试请先发布");
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (exam.getEndTime() != null && !now.isBefore(exam.getEndTime())) {
            // 仅"已结束"的考试走重新发布；进行中的考试无需重新发布
        } else {
            throw new IllegalArgumentException("考试仍在进行中，无需重新发布");
        }

        if (days != null) {
            // 模式一：发布时长（天数），开始时间保持不变，结束 = 当前 + N 天
            if (days < 1) {
                throw new IllegalArgumentException("发布天数至少为 1 天");
            }
            examMapper.updateSchedule(id, null, now.plusDays(days));
        } else if (startTime != null && endTime != null) {
            // 模式二：自定义时间窗口（与发布考试的时间窗口规则一致）
            if (!startTime.isBefore(endTime)) {
                throw new IllegalArgumentException("开始时间必须早于结束时间");
            }
            if (!endTime.isAfter(now)) {
                throw new IllegalArgumentException("结束时间必须晚于当前时间，否则无法重新发布");
            }
            examMapper.updateSchedule(id, startTime, endTime);
        } else {
            throw new IllegalArgumentException("请提供发布天数或自定义时间窗口");
        }
    }

    /**
     * 删除考试（级联清理该考试产生的全部数据）：
     * <ol>
     *     <li>答题明细 exam_answer（经 exam_record 定位）</li>
     *     <li>考试/成绩记录 exam_record（学生端"我的成绩"随考试删除而消失）</li>
     *     <li>考试监控 exam_student</li>
     *     <li>考试本身 exam</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        examAnswerMapper.deleteByExamId(id);
        examRecordMapper.deleteByExamId(id);
        examMonitorMapper.deleteByExamId(id);
        examMapper.deleteById(id);
    }

    @Override
    public long countAll(List<Long> courseIds) {
        // 教师只统计自己任教课程下的考试；管理员 courseIds 为 null（全站）
        return examMapper.countByCourses(courseIds);
    }

    /** 校验时间窗口，并从关联试卷快照字段 */
    private void validateAndSnapshot(Exam exam) {
        if (exam.getExamName() == null || exam.getExamName().isBlank()) {
            throw new IllegalArgumentException("考试名称不能为空");
        }
        if (exam.getPaperId() == null) {
            throw new IllegalArgumentException("必须选择一张试卷");
        }
        if (exam.getStartTime() == null || exam.getEndTime() == null) {
            throw new IllegalArgumentException("开始和结束时间不能为空");
        }
        if (!exam.getEndTime().isAfter(exam.getStartTime())) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }

        // 从试卷快照：总分、及格分、时长、学科
        ExamPaper paper = examPaperMapper.selectById(exam.getPaperId());
        if (paper == null) {
            throw new IllegalArgumentException("所选试卷不存在");
        }
        // 仅"已发布且已组卷"的试卷可用于发布考试
        if (paper.getStatus() == null || paper.getStatus() != 1) {
            throw new IllegalArgumentException("所选试卷尚未发布，请先在试卷管理中发布该试卷");
        }
        if (paperQuestionMapper.countByPaperId(exam.getPaperId()) == 0) {
            throw new IllegalArgumentException("所选试卷尚未组卷（无题目），请先完成组卷再发布考试");
        }
        exam.setTotalScore(paper.getTotalScore());
        // 及格分统一按满分 × 60% 快照（试卷历史数据为空/为 0 时兜底为 0），
        // 与班级学情统计的及格线口径一致
        exam.setPassScore(paper.getTotalScore() == null || paper.getTotalScore().compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO
                : paper.getTotalScore().multiply(new BigDecimal("0.6")).setScale(1, RoundingMode.HALF_UP));
        exam.setCourseId(paper.getCourseId());
        if (exam.getDuration() == null) {
            exam.setDuration(paper.getDuration());
        }
    }

    /** 填充运行状态：0未开始 1进行中 2已结束 3已撤回 */
    private void fillRuntimeStatus(Exam exam) {
        if (exam.getStatus() != null && exam.getStatus() == 0) {
            exam.setRuntimeStatus(3);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartTime())) {
            exam.setRuntimeStatus(0);
        } else if (now.isAfter(exam.getEndTime())) {
            exam.setRuntimeStatus(2);
        } else {
            exam.setRuntimeStatus(1);
        }
    }
}
