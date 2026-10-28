package com.exam.exam_system.service;

import com.exam.exam_system.dto.AiChatRequest;
import com.exam.exam_system.dto.AiExplainResult;
import com.exam.exam_system.dto.StudentExamDetail;
import com.exam.exam_system.dto.StudentRecordDetail;
import com.exam.exam_system.dto.SubmitRequest;
import com.exam.exam_system.dto.SubmitResult;
import com.exam.exam_system.entity.Exam;
import com.exam.exam_system.entity.ExamRecord;

import java.util.List;

/**
 * 学生端考试服务：看考试 → 拿题答题 → 交卷判分 → 查成绩
 */
public interface StudentExamService {

    /** 学生端考试列表（已发布的），附带“我是否已参加”标记 */
    List<Exam> listExamsForStudent(Long userId);

    /** 考试详情 + 题目列表（不含答案），附带已保存的答题进度 */
    StudentExamDetail getExamDetail(Long examId, Long userId);

    /** 保存答题进度（考试进行中逐题保存，刷新/断线后可恢复） */
    void saveProgress(Long examId, SubmitRequest req, Long userId);

    /** 交卷：客观题自动判分，主观题留待教师评分，写入 exam_record + exam_answer */
    SubmitResult submit(Long examId, SubmitRequest req, Long userId);

    /** 我的成绩 */
    List<ExamRecord> myRecords(Long userId);

    /**
     * 查看我的一次试卷详情：逐题作答、得分、教师评语，考试结束后附参考答案与解析
     *
     * <p>只能查自己的记录（按 userId 校验，防越权）。</p>
     */
    StudentRecordDetail getRecordDetail(Long recordId, Long userId);

    /**
     * 试卷回看里的 AI 讲解：就本次考卷中的某道题生成讲解
     *
     * <p>放行门槛与试卷回看完全一致（本人记录 + 已交卷 + 考试已结束）——
     * 讲解会把解题思路、参考答案和错因讲透，考试没结束时放行等于提前泄题。
     * 讲解会带上学生这道题的真实作答，让 AI 结合错因分析。</p>
     *
     * @param userId     当前登录学生
     * @param recordId   成绩记录 ID
     * @param questionId 题目 ID
     */
    AiExplainResult explainRecordQuestion(Long userId, Long recordId, Long questionId);

    /**
     * 试卷回看里的 AI 追问：携带历史对话继续提问
     *
     * <p>未配置大模型 api-key 时降级为本地规则引擎，仅首轮讲解可用。</p>
     */
    AiExplainResult chatRecordQuestion(Long userId, Long recordId, Long questionId,
                                       String question, List<AiChatRequest.Message> history);
}
