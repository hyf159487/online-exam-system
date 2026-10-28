package com.exam.exam_system.dto;

import lombok.Data;

import java.util.List;

/**
 * 单题知识点绑定项（AI 智能匹配"应用"时使用）
 *
 * <p>每道题携带自己的一组知识点（1~3 个，第一个为主知识点）；
 * 空列表表示清空该题全部知识点。
 */
@Data
public class QuestionKnowledgeItem {

    /** 题目 ID */
    private Long questionId;

    /** 该题要绑定的完整知识点 ID 列表（1~3 个；空=清空） */
    private List<Long> knowledgeIds;
}
