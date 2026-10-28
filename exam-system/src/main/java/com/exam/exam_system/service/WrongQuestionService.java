package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.TeacherWrongQuery;
import com.exam.exam_system.dto.WrongQuery;
import com.exam.exam_system.dto.WrongRedoRequest;
import com.exam.exam_system.dto.WrongRedoResult;
import com.exam.exam_system.entity.WrongQuestion;

import java.util.List;
import java.util.Map;

/**
 * 错题本服务：学生交卷后归集错题、学生查看/重做/导出错题
 */
public interface WrongQuestionService {

    /** 分页查询当前学生的错题本（可按学科/题型/错误次数筛选） */
    PageResult<WrongQuestion> page(Long userId, WrongQuery q);

    /** 按学科统计错题数量 */
    List<Map<String, Object>> stats(Long userId);

    /** 错题本总览：错题总数 / 已做对题数 / 累计答错次数 / 各学科分布 */
    Map<String, Object> overview(Long userId);

    /** 学生重做正确后，从错题本移除一条 */
    void remove(Long userId, Long id);

    /** 批量移除错题（只删自己的），返回实际删除条数 */
    int batchRemove(Long userId, List<Long> ids);

    /** 导出用：按筛选条件取全部错题（不分页） */
    List<WrongQuestion> listForExport(Long userId, WrongQuery q);

    /**
     * 错题本重做：提交答案 → 判分。
     * 答错：刷新记录（wrong_count+1，连续答对次数清零）；
     * 答对：correct_count+1，达到学生策略阈值则自动移出。
     */
    WrongRedoResult redo(Long userId, Long id, WrongRedoRequest req);

    /**
     * 交卷时归集一道错题（内部用）。
     * 同一学生对同一道题去重：已存在则刷新答案，不存在才插入。
     */
    void collect(WrongQuestion w);

    /** 教师端：全班错题分页列表（可按学生/班级/学科/题型筛选） */
    PageResult<Map<String, Object>> teacherPage(TeacherWrongQuery q);

    /** 教师端：错题统计（按学科分布 + 高频错题 Top10） */
    Map<String, Object> teacherStats(TeacherWrongQuery q);
}
