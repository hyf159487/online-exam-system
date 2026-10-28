package com.exam.exam_system.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量设置题目知识点请求体
 *
 * <p>将 ids 中所有题目统一绑定到同一组知识点 knowledgeIds（1~3 个，第一个为主知识点）。
 * 兼容旧字段 knowledgeId：若调用方只传单个 knowledgeId，则视为只有一个知识点。
 */
@Data
public class BatchKnowledgeReq {

    /** 需要设置知识点的题目 ID 列表 */
    private List<Long> ids;

    /** 要绑定的完整知识点 ID 列表（1~3 个，优先使用本字段） */
    private List<Long> knowledgeIds;

    /** 兼容旧版：单个知识点 ID（knowledgeIds 为空时自动转为单元素列表） */
    private Long knowledgeId;
}
