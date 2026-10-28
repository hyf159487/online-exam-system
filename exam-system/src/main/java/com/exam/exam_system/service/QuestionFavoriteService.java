package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.FavoriteQuery;
import com.exam.exam_system.entity.QuestionFavorite;

import java.util.List;
import java.util.Map;

/**
 * 试题收藏服务：学生在练习过程中收藏/取消收藏试题，并在「我的收藏」页面集中复习、导出。
 */
public interface QuestionFavoriteService {

    /** 分页查询当前学生的收藏（可按学科/题型/题干关键词筛选） */
    PageResult<QuestionFavorite> page(Long userId, FavoriteQuery q);

    /** 收藏总览：收藏总题数 + 各学科分布 */
    Map<String, Object> overview(Long userId);

    /**
     * 收藏一道题（幂等）。
     * @return true = 本次新收藏；false = 之前已收藏（直接提示「已收藏」即可）
     */
    boolean add(Long userId, Long questionId);

    /** 取消收藏（按收藏记录 ID，只删自己的） */
    void removeById(Long userId, Long id);

    /** 取消收藏（按试题 ID，练习页按钮用）；返回实际删除条数 */
    int removeByQuestion(Long userId, Long questionId);

    /** 批量取消收藏（只删自己的），返回实际删除条数 */
    int batchRemove(Long userId, List<Long> ids);

    /** 当前学生已收藏的全部试题 ID（练习页标记星标用） */
    List<Long> listQuestionIds(Long userId);

    /** 导出用：按筛选条件取全部收藏（不分页） */
    List<QuestionFavorite> listForExport(Long userId, FavoriteQuery q);
}
