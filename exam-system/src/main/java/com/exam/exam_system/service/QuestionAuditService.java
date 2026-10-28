package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.QuestionQuery;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.entity.QuestionAuditLog;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

/**
 * 试题审核服务接口
 */
public interface QuestionAuditService {

    /**
     * 待审核试题列表（分页）
     */
    PageResult<Question> waitList(QuestionQuery query);

    /**
     * 审核历史流水记录（分页，含通过/驳回/重新提交）
     */
    PageResult<QuestionAuditLog> historyList(QuestionQuery query);

    /**
     * 审核统计（统计卡片）：总审核条数、通过数、驳回数、重新提交次数
     */
    Map<String, Long> stats();

    /**
     * 某道题的完整审核流水（时间正序，详情弹窗时间线用）
     */
    List<QuestionAuditLog> listLogsByQuestionId(Long questionId);

    /**
     * 教师重新提交审核（状态重置为待审核 + 写入"重新提交"流水）
     */
    void resubmit(Long questionId, Long submitterId);

    /**
     * 审核通过（写入审核流水）
     */
    void pass(Long questionId, Long auditorId);

    /**
     * 审核驳回（必填驳回原因，写入审核流水）
     */
    void reject(Long questionId, String opinion, Long auditorId);

    /**
     * 批量审核通过（事务，全部成功或全部回滚，逐条写入流水）
     *
     * @return 实际通过数量
     */
    int batchPass(List<Long> questionIds, Long auditorId);

    /**
     * 批量审核驳回（事务，全部成功或全部回滚，逐条写入流水）
     *
     * @return 实际驳回数量
     */
    int batchReject(List<Long> questionIds, String opinion, Long auditorId);

    /**
     * 导出审核历史流水 Excel
     */
    void exportHistory(QuestionQuery query, HttpServletResponse response);
}
