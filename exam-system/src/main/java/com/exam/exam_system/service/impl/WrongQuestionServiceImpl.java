package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.TeacherWrongQuery;
import com.exam.exam_system.dto.WrongQuery;
import com.exam.exam_system.dto.WrongRedoRequest;
import com.exam.exam_system.dto.WrongRedoResult;
import com.exam.exam_system.entity.WrongQuestion;
import com.exam.exam_system.mapper.SysUserMapper;
import com.exam.exam_system.mapper.WrongQuestionMapper;
import com.exam.exam_system.service.WrongQuestionService;
import com.exam.exam_system.util.AnswerJudge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 错题本服务实现
 */
@Service
public class WrongQuestionServiceImpl implements WrongQuestionService {

    @Autowired
    private WrongQuestionMapper wrongQuestionMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Override
    public PageResult<WrongQuestion> page(Long userId, WrongQuery q) {
        int pn = q.getPageNum() <= 0 ? 1 : q.getPageNum();
        int ps = q.getPageSize() <= 0 ? 10 : q.getPageSize();
        q.setPageNum(pn);
        q.setPageSize(ps);

        List<WrongQuestion> records = wrongQuestionMapper.listByUser(userId, q);
        long total = wrongQuestionMapper.countByUser(userId, q);
        return PageResult.of(records, total, (long) pn, (long) ps);
    }

    @Override
    public List<Map<String, Object>> stats(Long userId) {
        return wrongQuestionMapper.statsByCourse(userId);
    }

    @Override
    public Map<String, Object> overview(Long userId) {
        Map<String, Object> s = wrongQuestionMapper.overviewByUser(userId);
        Map<String, Object> r = new HashMap<>();
        r.put("total", s == null ? 0L : toLong(s.get("total")));
        r.put("mastered", s == null ? 0L : toLong(s.get("mastered")));
        r.put("wrongTotal", s == null ? 0L : toLong(s.get("wrongTotal")));
        r.put("courses", wrongQuestionMapper.statsByCourse(userId));
        return r;
    }

    @Override
    public void remove(Long userId, Long id) {
        wrongQuestionMapper.deleteById(id, userId);
    }

    @Override
    public int batchRemove(Long userId, List<Long> ids) {
        if (ids == null) return 0;
        List<Long> clean = ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        return clean.isEmpty() ? 0 : wrongQuestionMapper.deleteByIds(userId, clean);
    }

    @Override
    public List<WrongQuestion> listForExport(Long userId, WrongQuery q) {
        return wrongQuestionMapper.listForExport(userId, q);
    }

    @Override
    public WrongRedoResult redo(Long userId, Long id, WrongRedoRequest req) {
        WrongQuestion w = wrongQuestionMapper.findByIdAndUser(id, userId);
        WrongRedoResult result = new WrongRedoResult();
        if (w == null) {
            result.setCorrect(false);
            result.setRemoved(false);
            return result;
        }
        boolean correct = AnswerJudge.judge(w.getQuestionType(), req.getUserAnswer(), w.getCorrectAnswer());
        result.setCorrect(correct);
        result.setCorrectAnswer(w.getCorrectAnswer());
        result.setUserAnswer(req.getUserAnswer());

        if (correct) {
            // 做对一次：correct_count +1
            wrongQuestionMapper.incrementCorrectCount(id, userId);
            WrongQuestion after = wrongQuestionMapper.findByIdAndUser(id, userId);
            int correctCount = after != null ? (after.getCorrectCount() == null ? 0 : after.getCorrectCount()) : 0;
            result.setCorrectCount(correctCount);

            // 读取学生策略：开启自动移除且达到阈值 → 移出
            com.exam.exam_system.entity.SysUser u = sysUserMapper.selectById(userId);
            boolean auto = u != null && u.getWrongAutoRemove() != null && u.getWrongAutoRemove() == 1;
            int threshold = normalizeThreshold(u == null ? null : u.getWrongRemoveThreshold());
            if (auto && correctCount >= threshold) {
                wrongQuestionMapper.deleteById(id, userId);
                result.setRemoved(true);
            } else {
                result.setRemoved(false);
            }
        } else {
            // 又答错：刷新答案为最新错答，wrong_count +1，last_wrong_time 刷新
            w.setUserAnswer(req.getUserAnswer());
            wrongQuestionMapper.updateAnswer(w);
            result.setCorrectCount(0);
            result.setRemoved(false);
        }
        return result;
    }

    @Override
    public void collect(WrongQuestion w) {
        // 同一学生对同一道题只保留一条：已存在则刷新答案，否则插入
        WrongQuestion exist = wrongQuestionMapper.findByUserAndQuestion(w.getUserId(), w.getQuestionId());
        if (exist != null) {
            // 再次答错：刷新答案/题型/学科/来源（wrong_count 与 last_wrong_time 由 SQL 自增/刷新）
            exist.setUserAnswer(w.getUserAnswer());
            exist.setCorrectAnswer(w.getCorrectAnswer());
            exist.setScore(w.getScore());
            exist.setQuestionType(w.getQuestionType());
            exist.setCourseId(w.getCourseId());
            exist.setRecordId(w.getRecordId());
            wrongQuestionMapper.updateAnswer(exist);
        } else {
            wrongQuestionMapper.insert(w);
        }
    }

    @Override
    public PageResult<Map<String, Object>> teacherPage(TeacherWrongQuery q) {
        int pn = q.getPageNum() <= 0 ? 1 : q.getPageNum();
        int ps = q.getPageSize() <= 0 ? 10 : q.getPageSize();
        q.setPageNum(pn);
        q.setPageSize(ps);
        List<Map<String, Object>> records = wrongQuestionMapper.listByTeacher(q);
        long total = wrongQuestionMapper.countByTeacher(q);
        return PageResult.of(records, total, (long) pn, (long) ps);
    }

    @Override
    public Map<String, Object> teacherStats(TeacherWrongQuery q) {
        Map<String, Object> result = new HashMap<>();
        result.put("courseStats", wrongQuestionMapper.statsCourseByTeacher(q));
        result.put("topQuestions", wrongQuestionMapper.statsTopQuestionsByTeacher(q));
        return result;
    }

    /** 阈值收敛到 1~10（默认 3），避免历史脏数据导致策略失效 */
    private int normalizeThreshold(Integer t) {
        if (t == null) return 3;
        if (t < 1) return 1;
        return Math.min(t, 10);
    }

    /** Map 里的 COUNT/SUM 是 Number 子类，统一转 long */
    private long toLong(Object v) {
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }
}
