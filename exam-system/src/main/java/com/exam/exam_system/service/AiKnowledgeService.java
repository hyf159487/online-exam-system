package com.exam.exam_system.service;

import com.exam.exam_system.dto.KnowledgeMatchResult;
import com.exam.exam_system.entity.Question;

import java.util.List;

/**
 * AI 知识点匹配 Service
 *
 * <p>根据题目（题干/选项/答案/解析）从所属学科的知识点中自动匹配最合适的一个知识点，
 * 用于批量给历史遗漏设置知识点的题目补齐关联，减少逐个编辑的成本。
 *
 * <p>匹配策略：
 * <ul>
 *     <li>已配置 ai.api-key：调用大模型，按学科分组，综合题目内容与知识点名称语义匹配（更准确）；</li>
 *     <li>未配置或调用失败：降级为本地关键词相似度规则，保证离线可用。</li>
 * </ul>
 */
public interface AiKnowledgeService {

    /**
     * 为一组题目匹配知识点
     *
     * @param questions 题目列表（需含 id、courseId、content、options、answer、analysis）
     * @return 与输入顺序一致的匹配结果列表
     */
    List<KnowledgeMatchResult> match(List<Question> questions);
}
