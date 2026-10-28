package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.QuestionKnowledgeItem;
import com.exam.exam_system.dto.QuestionQuery;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.mapper.QuestionMapper;
import com.exam.exam_system.service.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class QuestionServiceImpl implements QuestionService {

    /** 一道题最多关联的知识点个数 */
    public static final int MAX_KNOWLEDGE_PER_QUESTION = 3;

    @Autowired
    private QuestionMapper questionMapper;

    @Override
    public PageResult<Question> page(QuestionQuery query) {
        // 1. 拿总数
        long total = questionMapper.countByQuery(query);
        // 2. 拿当前页数据
        List<Question> records = questionMapper.listByQuery(query);
        // 3. 把知识点 ID 逗号串拆成数组，供前端多选回显/列表展示
        if (records != null) {
            records.forEach(QuestionServiceImpl::fillKnowledgeIds);
        }
        // 4. 封成 PageResult（泛型擦除后字段都是 Long，强转下）
        Long pn = query.getPageNum() == null ? 1L : query.getPageNum().longValue();
        Long ps = query.getPageSize() == null ? 10L : query.getPageSize().longValue();
        return PageResult.of(records, total, pn, ps);
    }

    @Override
    public Question getById(Long id) {
        Question q = questionMapper.selectById(id);
        if (q != null) {
            fillKnowledgeIds(q);
        }
        return q;
    }

    @Override
    @Transactional
    public void add(Question q) {
        // 知识点统一口径：knowledgeIds 优先，否则兼容旧字段 knowledgeId
        List<Long> kids = normalize(q);
        q.setKnowledgeId(firstOf(kids));
        questionMapper.insert(q);
        replaceRels(q.getId(), kids);
    }

    @Override
    @Transactional
    public void update(Question q) {
        List<Long> kids = normalize(q);
        q.setKnowledgeId(firstOf(kids));
        questionMapper.updateById(q);
        replaceRels(q.getId(), kids);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        questionMapper.deleteRelsByQuestionId(id);
        questionMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        questionMapper.deleteRelsByQuestionIds(ids);
        questionMapper.batchDeleteByIds(ids);
    }

    @Override
    public void batchSetKnowledge(List<Long> ids, Long knowledgeId) {
        batchSetKnowledgeList(ids, knowledgeId == null ? Collections.emptyList() : Collections.singletonList(knowledgeId));
    }

    @Override
    @Transactional
    public void batchSetKnowledgeList(List<Long> ids, List<Long> knowledgeIds) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<Long> kids = trim(ids != null && knowledgeIds != null ? knowledgeIds : Collections.emptyList());
        // 先整体清掉这些题的知识点关联，再统一插入新集合
        questionMapper.deleteRelsByQuestionIds(ids);
        questionMapper.updateKnowledgeBatch(ids, firstOf(kids));
        if (!kids.isEmpty()) {
            for (Long id : ids) {
                questionMapper.insertRels(id, kids);
            }
        }
    }

    @Override
    @Transactional
    public void batchSetKnowledgeItems(List<QuestionKnowledgeItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (QuestionKnowledgeItem item : items) {
            if (item.getQuestionId() == null) {
                continue;
            }
            List<Long> kids = trim(item.getKnowledgeIds());
            List<Long> single = Collections.singletonList(item.getQuestionId());
            questionMapper.deleteRelsByQuestionId(item.getQuestionId());
            questionMapper.updateKnowledgeBatch(single, firstOf(kids));
            if (!kids.isEmpty()) {
                questionMapper.insertRels(item.getQuestionId(), kids);
            }
        }
    }

    @Override
    @Transactional
    public void addBatch(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        for (Question q : questions) {
            q.setKnowledgeId(firstOf(normalize(q)));
        }
        questionMapper.batchInsert(questions);
        // 关联表：按每道题的主/多知识点回插
        for (Question q : questions) {
            List<Long> kids = trim(q.getKnowledgeIds());
            if (kids.isEmpty() && q.getKnowledgeId() != null) {
                kids = Collections.singletonList(q.getKnowledgeId());
            }
            if (!kids.isEmpty() && q.getId() != null) {
                questionMapper.insertRels(q.getId(), kids);
            }
        }
    }

    // ==================== 知识点集合与关联表维护工具 ====================

    /** 题目新增/更新时的知识点归一：knowledgeIds 数组优先，兼容旧单值 knowledgeId；去重、限 3 个 */
    private static List<Long> normalize(Question q) {
        List<Long> src = q.getKnowledgeIds() != null ? q.getKnowledgeIds()
                : (q.getKnowledgeId() != null ? Collections.singletonList(q.getKnowledgeId()) : Collections.emptyList());
        return trim(src);
    }

    /** 去重并限制最多 MAX_KNOWLEDGE_PER_QUESTION 个，保持顺序 */
    private static List<Long> trim(List<Long> src) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        for (Long v : src) {
            if (v != null) {
                set.add(v);
                if (set.size() >= MAX_KNOWLEDGE_PER_QUESTION) {
                    break;
                }
            }
        }
        return new ArrayList<>(set);
    }

    private static Long firstOf(List<Long> kids) {
        return kids == null || kids.isEmpty() ? null : kids.get(0);
    }

    /** 重建某题的知识点关联（先清后插）；空集合 = 清除全部 */
    private void replaceRels(Long questionId, List<Long> kids) {
        if (questionId == null) {
            return;
        }
        questionMapper.deleteRelsByQuestionId(questionId);
        if (!kids.isEmpty()) {
            questionMapper.insertRels(questionId, kids);
        }
    }

    /** 把 SQL GROUP_CONCAT 的 ID 逗号串解析回填到 knowledgeIds */
    private static void fillKnowledgeIds(Question q) {
        if (q == null || q.getKnowledgeIdsCsv() == null || q.getKnowledgeIdsCsv().isBlank()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (String s : q.getKnowledgeIdsCsv().split(",")) {
            try {
                ids.add(Long.valueOf(s.trim()));
            } catch (NumberFormatException ignored) {
                // 忽略脏数据
            }
        }
        q.setKnowledgeIds(ids);
        q.setKnowledgeIdsCsv(null);
    }
}
