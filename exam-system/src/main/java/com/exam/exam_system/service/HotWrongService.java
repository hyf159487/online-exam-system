package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.HotWrongItem;
import com.exam.exam_system.dto.HotWrongQuery;

/**
 * 高频错题服务
 */
public interface HotWrongService {

    /**
     * 高频错题分页列表
     */
    PageResult<HotWrongItem> list(HotWrongQuery query);

    /**
     * 单题错误率统计
     */
    HotWrongItem statByQuestionId(Long questionId);
}
