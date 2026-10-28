package com.exam.exam_system.controller;

import com.exam.exam_system.common.BizException;
import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.QuestionQuery;
import com.exam.exam_system.entity.Question;
import com.exam.exam_system.service.QuestionAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 试题审核管理 Controller（审核写操作管理员专属；列表/详情/流水教师只读可用）
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET  /api/audit/waitList         待审核(0) / 已驳回(2) 试题列表（教师：仅自己提交的题目）</li>
 *   <li>GET  /api/audit/historyList     审核历史记录（管理员，支持审核结果/提交教师/审核人/时间范围/题干关键词）</li>
 *   <li>GET  /api/audit/export          导出审核历史 Excel（管理员）</li>
 *   <li>POST /api/audit/pass/{id}        审核通过（管理员）</li>
 *   <li>POST /api/audit/reject           审核驳回（管理员，传 id + 驳回原因）</li>
 *   <li>POST /api/audit/batchPass        批量审核通过（管理员）</li>
 *   <li>POST /api/audit/batchReject      批量审核驳回（管理员）</li>
 *   <li>GET  /api/audit/detail/{id}      试题详情（教师：仅自己提交的题目）</li>
 *   <li>GET  /api/audit/logs/{questionId} 某题审核流水（教师：仅自己提交的题目）</li>
 * </ul>
 *
 * <p>路径级权限由 AuthInterceptor 控制：仅 waitList / detail / logs 对教师开放，
 * 审核通过、驳回、历史记录、导出等仍限管理员。
 */
@RestController
@RequestMapping("/api/audit")
public class QuestionAuditController {

    @Autowired
    private QuestionAuditService auditService;

    @Autowired
    private com.exam.exam_system.service.QuestionService questionService;

    /**
     * 待审核试题列表
     *
     * <p>支持两种状态（auditStatus）：
     * <ul>
     *   <li>0 = 待审核（默认）：管理员待办队列；教师查看自己已提交待审的题目</li>
     *   <li>2 = 已驳回：教师查看被驳回的题目并修改后重提</li>
     * </ul>
     * 数据范围：管理员可按提交教师筛选全部；教师强制只看自己提交的题目（忽略传入的 creatorId）。
     */
    @GetMapping("/waitList")
    public Result<PageResult<Question>> waitList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer questionType,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long creatorId,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            HttpServletRequest request) {

        Integer role = (Integer) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");

        QuestionQuery query = buildQuery(pageNum, pageSize, questionType, difficulty,
                creatorId, null, keyword, startTime, endTime);
        query.setCourseId(courseId);
        // 只放行「待审核 / 已驳回」两种状态，其余近似值归为待审核
        query.setAuditStatus(auditStatus != null && auditStatus == 2 ? 2 : 0);
        if (role != null && role == 1) {
            // 教师端：只能看自己提交的题目，防止越权查看他人待审/驳回题目
            query.setCreatorId(userId);
        }
        PageResult<Question> result = auditService.waitList(query);
        return Result.success("查询成功", result);
    }

    /**
     * 审核历史流水记录（通过/驳回/重新提交，多条件筛选）
     */
    @GetMapping("/historyList")
    public Result<PageResult<com.exam.exam_system.entity.QuestionAuditLog>> historyList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) Integer questionType,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Long creatorId,
            @RequestParam(required = false) Long auditUserId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        QuestionQuery query = buildQuery(pageNum, pageSize, questionType, difficulty,
                creatorId, auditUserId, keyword, startTime, endTime);
        query.setAuditStatus(auditStatus);
        PageResult<com.exam.exam_system.entity.QuestionAuditLog> result = auditService.historyList(query);
        return Result.success("查询成功", result);
    }

    /**
     * 审核统计（统计卡片）：总审核条数、通过数、驳回数、重新提交次数
     */
    @GetMapping("/stats")
    public Result<java.util.Map<String, Long>> stats() {
        return Result.success("查询成功", auditService.stats());
    }

    /**
     * 某道题的完整审核流水（时间正序，详情弹窗时间线用）。
     * 教师端仅可查看自己提交题目的流水（数据范围见 {@link #checkTeacherOwnership}）
     */
    @GetMapping("/logs/{questionId}")
    public Result<List<com.exam.exam_system.entity.QuestionAuditLog>> logs(@PathVariable Long questionId,
                                                                          HttpServletRequest request) {
        Question q = questionService.getById(questionId);
        if (q == null) {
            return Result.error(404, "题目不存在");
        }
        String denied = checkTeacherOwnership(q, request);
        if (denied != null) {
            return Result.error(403, denied);
        }
        return Result.success("查询成功", auditService.listLogsByQuestionId(questionId));
    }

    /**
     * 导出审核历史 Excel（按当前筛选条件导出全部）
     */
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) Integer questionType,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Long creatorId,
            @RequestParam(required = false) Long auditUserId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            HttpServletResponse response) {

        QuestionQuery query = buildQuery(1, 10, questionType, difficulty,
                creatorId, auditUserId, keyword, startTime, endTime);
        query.setAuditStatus(auditStatus);
        auditService.exportHistory(query, response);
    }

    /**
     * 审核通过
     */
    @PostMapping("/pass/{id}")
    @com.exam.exam_system.annotation.OperationLog(
            module = "试题审核", type = "审核通过",
            description = "审核通过题目 id=#{#id}")
    public Result<Object> pass(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            auditService.pass(id, userId);
            return Result.success("审核通过", null);
        } catch (BizException e) {
            return Result.error(400, e.getMessage(), e.getData());
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 审核驳回
     * Body: { "questionId": 1, "opinion": "选项有歧义，请修改" }
     */
    @PostMapping("/reject")
    @com.exam.exam_system.annotation.OperationLog(
            module = "试题审核", type = "审核驳回",
            description = "驳回题目 id=#{#body.questionId}")
    public Result<Void> reject(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long questionId = body.get("questionId") != null
                ? ((Number) body.get("questionId")).longValue() : null;
        String opinion = body.get("opinion") != null ? body.get("opinion").toString() : null;

        if (questionId == null) {
            return Result.error(400, "题目ID不能为空");
        }
        if (opinion == null || opinion.isBlank()) {
            return Result.error(400, "驳回原因不能为空");
        }

        try {
            auditService.reject(questionId, opinion, userId);
            return Result.success("已驳回", null);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 批量审核通过
     * Body: { "ids": [1, 2, 3] }
     */
    @PostMapping("/batchPass")
    @com.exam.exam_system.annotation.OperationLog(
            module = "试题审核", type = "批量审核通过",
            description = "批量通过 #{#body.ids.size()} 道题目")
    public Result<Object> batchPass(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            List<Long> ids = extractIds(body);
            int count = auditService.batchPass(ids, userId);
            return Result.success("已批量通过 " + count + " 道题目", null);
        } catch (BizException e) {
            return Result.error(400, e.getMessage(), e.getData());
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 批量审核驳回
     * Body: { "ids": [1, 2, 3], "opinion": "题干表述不严谨" }
     */
    @PostMapping("/batchReject")
    @com.exam.exam_system.annotation.OperationLog(
            module = "试题审核", type = "批量审核驳回",
            description = "批量驳回 #{#body.ids.size()} 道题目")
    public Result<Void> batchReject(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String opinion = body.get("opinion") != null ? body.get("opinion").toString() : null;

        if (opinion == null || opinion.isBlank()) {
            return Result.error(400, "驳回原因不能为空");
        }

        try {
            List<Long> ids = extractIds(body);
            int count = auditService.batchReject(ids, opinion, userId);
            return Result.success("已批量驳回 " + count + " 道题目", null);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 试题详情（审核页/历史页共用）
     * 教师端仅可查看自己提交的题目（数据范围见 {@link #checkTeacherOwnership}）
     */
    @GetMapping("/detail/{id}")
    public Result<Question> detail(@PathVariable Long id, HttpServletRequest request) {
        Question q = questionService.getById(id);
        if (q == null) {
            return Result.error(404, "题目不存在");
        }
        String denied = checkTeacherOwnership(q, request);
        if (denied != null) {
            return Result.error(403, denied);
        }
        return Result.success("查询成功", q);
    }

    // ===== 内部工具 =====

    /**
     * 教师端数据范围校验：教师只能查看自己提交的题目。
     * 管理员（role=2）不受限制；无权限时返回提示文案，有权限返回 null。
     */
    private String checkTeacherOwnership(Question q, HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");
        if (role != null && role == 1
                && (q.getCreatorId() == null || !q.getCreatorId().equals(userId))) {
            return "只能查看自己提交的试题";
        }
        return null;
    }

    private QuestionQuery buildQuery(Integer pageNum, Integer pageSize, Integer questionType,
                                     Integer difficulty, Long creatorId, Long auditUserId,
                                     String keyword, String startTime, String endTime) {
        QuestionQuery query = new QuestionQuery();
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        query.setQuestionType(questionType);
        query.setDifficulty(difficulty);
        query.setCreatorId(creatorId);
        query.setAuditUserId(auditUserId);
        query.setKeyword(keyword);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        return query;
    }

    private List<Long> extractIds(Map<String, Object> body) {
        Object idsObj = body.get("ids");
        if (!(idsObj instanceof List<?> list) || list.isEmpty()) {
            throw new RuntimeException("请先勾选要审核的题目");
        }
        return list.stream().map(v -> ((Number) v).longValue()).toList();
    }
}
