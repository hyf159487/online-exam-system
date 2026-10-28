package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.QuestionKnowledgeItem;
import com.exam.exam_system.dto.QuestionQuery;
import com.exam.exam_system.entity.Question;

import java.util.List;

/**
 * 题目 Service 接口
 */
public interface QuestionService {

    /** 分页查题目 */
    PageResult<Question> page(QuestionQuery query);

    /** 按 ID 查单个题目（编辑回显用） */
    Question getById(Long id);

    /** 新增题目 */
    void add(Question q);

    /** 更新题目 */
    void update(Question q);

    /** 删除题目 */
    void delete(Long id);

    /** 批量删除题目 */
    void deleteBatch(List<Long> ids);

    /** 批量新增题目 */
    void addBatch(List<Question> questions);

    /**
     * 批量设置题目知识点（兼容旧版：统一绑定到单个知识点，不改动审核状态）
     */
    void batchSetKnowledge(List<Long> ids, Long knowledgeId);

    /**
     * 批量设置题目知识点（多选版：把所有题统一绑定到同一组知识点 knowledgeIds，
     * 1~3 个，第一个为主知识点；空列表=清空，不改动审核状态）
     */
    void batchSetKnowledgeList(List<Long> ids, List<Long> knowledgeIds);

    /**
     * 逐题设置知识点组合（AI 智能匹配"应用"用；每道题携带各自的知识点集合）
     */
    void batchSetKnowledgeItems(List<QuestionKnowledgeItem> items);
}