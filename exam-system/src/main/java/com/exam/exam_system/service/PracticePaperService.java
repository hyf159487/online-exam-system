package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.PracticeAttachment;
import com.exam.exam_system.dto.PracticePaperSaveRequest;
import com.exam.exam_system.dto.PracticeReviewItem;
import com.exam.exam_system.dto.RandomComposeRequest;
import com.exam.exam_system.entity.PracticeAnswer;
import com.exam.exam_system.entity.PracticePaper;
import com.exam.exam_system.entity.PracticeRecord;
import com.exam.exam_system.entity.Question;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 练习卷管理服务（教师/管理员端）
 */
public interface PracticePaperService {

    /** 练习卷分页列表 */
    PageResult<PracticePaper> listPaper(int pageNum, int pageSize,
                                        Long teacherId, Integer role,
                                        String keyword, Long courseId, Integer status,
                                        Long studentId);

    /** 新增/编辑练习卷 */
    Long savePaper(PracticePaperSaveRequest request, Long teacherId, String teacherName, Integer role);

    /** 发布/停用切换 */
    void publish(Long id, Integer targetStatus, Long teacherId, Integer role);

    /** 逻辑删除 */
    void deletePaper(Long id, Long teacherId, Integer role);

    /** 练习卷详情（含题目） */
    PracticePaper detail(Long id);

    /**
     * 上传作业附件（文件型作业）：把文件落盘到 uploads/homework/yyyyMM/ 并返回元数据，
     * 保存作业时由前端把元数据回传到 savePaper 一并入库。
     *
     * @param file 作业文件（Word/PDF/PPT/Excel/图片/压缩包等）
     * @return 附件元数据（文件名、访问路径、大小、类型）
     */
    PracticeAttachment uploadAttachment(MultipartFile file);

    /** 按「题型+难度+数量」规则从题库随机抽题，返回题目列表（不落库），供作业随机选题使用 */
    List<Question> pickRandom(Long courseId, List<RandomComposeRequest.Rule> rules);

    // ===== 练习记录 =====

    PageResult<PracticeRecord> listRecord(int pageNum, int pageSize,
                                          Long teacherId, Integer role,
                                          Long paperId, Long studentId, String keyword);

    List<PracticeAnswer> recordDetail(Long recordId, Long teacherId, Integer role);

    // ===== 批改作业 =====

    /** 批改工作台列表（reviewStatus：1待批改 2已批改 0无需批改，null=全部） */
    PageResult<PracticeRecord> listReview(int pageNum, int pageSize,
                                          Long teacherId, Integer role,
                                          Long paperId, String keyword, Integer reviewStatus);

    /** 提交批改：逐题写入得分/评语，并重算该次作业的总分与对错题数 */
    void submitReview(Long recordId, List<PracticeReviewItem> items, Long teacherId, Integer role);

    /**
     * 批改文件型作业提交（学生上传的作业文件）：给分 + 评语。
     * 文件型作业没有逐题明细，得分/评语直接落在 practice_record 上，并把 review_status 置为 2 已批改。
     *
     * @param score 0 ~ 满分（作业未设总分时上限按 100 分制）
     */
    void reviewAttachmentSubmit(Long recordId, Integer score, String opinion, Long teacherId, Integer role);

    /**
     * 教师端下载用：读取一条「学生提交的作业文件」记录，并做数据权限校验。
     * 仅文件型提交（attachment_path 非空）可下载；教师只能下载自己发布作业的提交。
     *
     * @return 提交记录（含 attachmentName 原始文件名 与 attachmentPath 访问路径）
     */
    PracticeRecord loadSubmittedAttachment(Long recordId, Long teacherId, Integer role);
}
