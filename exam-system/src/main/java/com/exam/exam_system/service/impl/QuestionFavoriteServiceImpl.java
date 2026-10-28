package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.FavoriteQuery;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.entity.QuestionFavorite;
import com.exam.exam_system.mapper.QuestionFavoriteMapper;
import com.exam.exam_system.mapper.QuestionMapper;
import com.exam.exam_system.service.QuestionFavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 试题收藏服务实现
 *
 * <p>收藏 = 学生手动标记的重点题；与错题本（系统自动归集答错的题）互不影响，
 * 两者在菜单上并列，构成「错题 + 收藏」两套复习体系。
 */
@Service
public class QuestionFavoriteServiceImpl implements QuestionFavoriteService {

    @Autowired
    private QuestionFavoriteMapper questionFavoriteMapper;

    @Autowired
    private QuestionMapper questionMapper;

    @Override
    public PageResult<QuestionFavorite> page(Long userId, FavoriteQuery q) {
        int pn = (q.getPageNum() == null || q.getPageNum() <= 0) ? 1 : q.getPageNum();
        int ps = (q.getPageSize() == null || q.getPageSize() <= 0) ? 10 : q.getPageSize();
        q.setPageNum(pn);
        q.setPageSize(ps);
        normalizeKeyword(q);

        List<QuestionFavorite> records = questionFavoriteMapper.listByUser(userId, q);
        long total = questionFavoriteMapper.countByUser(userId, q);
        return PageResult.of(records, total, (long) pn, (long) ps);
    }

    @Override
    public Map<String, Object> overview(Long userId) {
        Map<String, Object> s = questionFavoriteMapper.overviewByUser(userId);
        Map<String, Object> r = new HashMap<>();
        r.put("total", s == null ? 0L : toLong(s.get("total")));
        r.put("courses", questionFavoriteMapper.statsByCourse(userId));
        return r;
    }

    @Override
    public boolean add(Long userId, Long questionId) {
        if (questionId == null) return false;
        // 只允许收藏真实存在的试题，避免脏数据（收藏表与 question 表靠 JOIN 回显）
        Question q = questionMapper.selectById(questionId);
        if (q == null) return false;
        return questionFavoriteMapper.insertIfAbsent(userId, questionId) > 0;
    }

    @Override
    public void removeById(Long userId, Long id) {
        questionFavoriteMapper.deleteByIdAndUser(id, userId);
    }

    @Override
    public int removeByQuestion(Long userId, Long questionId) {
        if (questionId == null) return 0;
        return questionFavoriteMapper.deleteByUserAndQuestion(userId, questionId);
    }

    @Override
    public int batchRemove(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        List<Long> clean = ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        return clean.isEmpty() ? 0 : questionFavoriteMapper.deleteByIds(userId, clean);
    }

    @Override
    public List<Long> listQuestionIds(Long userId) {
        List<Long> ids = questionFavoriteMapper.listQuestionIds(userId);
        return ids == null ? Collections.emptyList() : ids;
    }

    @Override
    public List<QuestionFavorite> listForExport(Long userId, FavoriteQuery q) {
        normalizeKeyword(q);
        return questionFavoriteMapper.listForExport(userId, q);
    }

    /** 题干关键词归一：空白串 → null（mapper 里只判非空，避免 XML 里写引号转义） */
    private void normalizeKeyword(FavoriteQuery q) {
        if (q == null) return;
        String kw = q.getKeyword();
        if (kw == null) return;
        String t = kw.trim();
        q.setKeyword(t.isEmpty() ? null : t);
    }

    /** Map 里的 COUNT 是 Number 子类，统一转 long */
    private long toLong(Object v) {
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }
}
