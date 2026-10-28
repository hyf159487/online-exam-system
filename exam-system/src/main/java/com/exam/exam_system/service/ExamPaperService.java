package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.PaperDetail;
import com.exam.exam_system.dto.PaperQuestionSaveRequest;
import com.exam.exam_system.dto.RandomComposeRequest;
import com.exam.exam_system.entity.ExamPaper;

import java.util.List;

public interface ExamPaperService {

    /**
     * 最近 N 张试卷（教师端首页用）
     *
     * @param courseIds 数据权限：只查这些课程下的试卷；null = 不限制（管理员）
     */
    List<ExamPaper> listRecent(int limit, List<Long> courseIds);

    /** 教师查看自己创建的试卷 */
    List<ExamPaper> listByCreator(Long creatorId);

    /** 分页查询试卷（可选：按名称模糊、按状态、按学科）
     *  权限隔离：管理员(role=2)看全部；教师只看自己任教课程下、且自己创建或被共享给自己的 */
    PageResult<ExamPaper> page(Integer pageNum, Integer pageSize, String paperName, Integer status,
                               Long courseId, Long userId, Integer role, List<Long> courseIds);

    /** 按 ID 查试卷（写权限校验用） */
    ExamPaper getById(Long id);

    /** 试卷详情：基本信息 + 已选题目列表 */
    PaperDetail detail(Long id);

    /** 新增试卷（默认草稿状态） */
    void add(ExamPaper paper);

    /** 更新试卷基本信息 */
    void update(ExamPaper paper);

    /** 删除试卷（智能选择：有历史考试记录→逻辑删除；无→物理删除；事务） */
    void delete(Long id);

    /** 发布试卷（草稿 → 已发布） */
    void publish(Long id);

    /** 撤回试卷（已发布 → 草稿；已归档不可撤回） */
    void revoke(Long id);

    /** 归档试卷（已发布 → 已归档；归档后不可改/不可组卷/不可用于新建考试） */
    void archive(Long id);

    /** 批量归档试卷 */
    int archiveBatch(List<Long> ids);

    /** 校验试卷是否允许组卷（进行中考试占用→抛异常阻断） */
    void checkComposable(Long paperId);

    /** 保存组卷结果（先清空旧关联再插入，自动更新总分，事务） */
    void saveQuestions(PaperQuestionSaveRequest request);

    /**
     * 随机组卷：按题型/难度/数量规则从题库随机抽题，覆盖式生成试卷题目（事务）
     *
     * @param paperId 试卷ID
     * @param rules   抽题规则（题型+难度+数量+每题分值）
     * @return 实际抽到的题目总数
     */
    int randomCompose(Long paperId, List<RandomComposeRequest.Rule> rules);

    /**
     * 批量删除试卷：删除多张试卷及其下所有题目关联
     * @return 实际删除的试卷数
     */
    int deleteBatch(List<Long> ids);
}
