package com.exam.exam_system.service;

import com.exam.exam_system.dto.CoursePracticeStats;
import com.exam.exam_system.dto.PracticeConfig;
import com.exam.exam_system.dto.PracticeQuestionQuery;
import com.exam.exam_system.dto.PracticeSubmitRequest;
import com.exam.exam_system.dto.PracticeSubmitResult;
import com.exam.exam_system.dto.QuestionPracticeStat;
import com.exam.exam_system.entity.PracticeRecord;
import com.exam.exam_system.entity.Question;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 自主练习服务 */
public interface PracticeService {

    /**
     * 按筛选条件随机抽取练习题目（不含答案）：学科 + 题型(多选) + 难度(多选) + 可选知识点 + 题量。
     * 限定在该学生已加入课程的题库范围内抽取。
     */
    List<Question> getPracticeQuestions(Long studentId, PracticeQuestionQuery query);

    /** 提交单题作答：判分 + 答错归集错题本 */
    PracticeSubmitResult submit(Long userId, PracticeSubmitRequest req);

    /** 某学科练习统计：题库总题数 + 该学生已作答过去重题数 */
    CoursePracticeStats courseStats(Long userId, Long courseId);

    /** 某道题的练习统计：该学生总作答次数 + 答对次数（答题后展示「已做过几次/正确率」） */
    QuestionPracticeStat questionStat(Long userId, Long questionId);

    /** 读取学生错题自动移除策略 */
    PracticeConfig getConfig(Long userId);

    /** 保存学生错题自动移除策略（校验 0/1 与 1/3） */
    PracticeConfig saveConfig(Long userId, PracticeConfig cfg);

    /**
     * 提交文件型作业：上传学生做好的作业文件，落一条 practice_record（review_status = 1 待教师批改）。
     * 同一份作业重复提交时覆盖上一条附件（沿用同一记录ID），教师端可在「批改作业」中查看与给分。
     *
     * @param studentId   当前登录学生ID
     * @param studentName 学生姓名（落库冗余，教师端列表展示）
     * @param paperId     作业ID（必须是已发布的附件型作业）
     * @param file        学生提交的作业文件
     * @param remark      提交备注（选填，<=255 字）
     */
    PracticeRecord submitPaperAttachment(Long studentId, String studentName, Long paperId,
                                         MultipartFile file, String remark);
}
