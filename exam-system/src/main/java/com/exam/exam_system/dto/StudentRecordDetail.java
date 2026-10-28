package com.exam.exam_system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生端「查看试卷」：一次考试的完整答卷回看
 *
 * <p>用途：教师批阅完成后，学生在「我的成绩」里点开一条成绩，逐题查看
 * 自己的作答、得分、教师评语，以及参考答案与解析。</p>
 *
 * <p>可见性规则（由 Service 控制，前端只负责展示）：
 * <ul>
 *   <li>主观题未批阅时 {@code getScore} 下发 null（不下发 0 分，避免学生误以为得了 0 分）；</li>
 *   <li>参考答案与解析仅在考试结束后下发（{@code answerVisible=true}），
 *       防止已交卷学生把答案传给仍在考试的同学；</li>
 *   <li>排名仅在全部批阅完成（{@code fullyReviewed=true}）时给出，
 *       否则同卷其他同学分数还没判完，排名失真。</li>
 * </ul>
 */
@Data
public class StudentRecordDetail {

    // ========== 成绩概览 ==========

    private Long recordId;

    private Long examId;

    private Long paperId;

    /** 试卷名（交卷时的快照，试卷被删也不丢） */
    private String paperName;

    /** 当前总分（含客观题 + 已批阅主观题；未批阅完时是中间分） */
    private BigDecimal totalScore;

    /** 试卷满分 */
    private BigDecimal paperTotalScore;

    /** 及格分 */
    private BigDecimal passScore;

    /** 1=及格 0=不及格 */
    private Integer isPass;

    /** 1=已交卷待阅卷 2=已判分 */
    private Integer status;

    /** 本卷排名（同分并列；仅 fullyReviewed 时给出，否则为 null） */
    private Integer rank;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submitTime;

    // ========== 批阅进度 ==========

    /** 主观题总数 */
    private Integer subjectiveCount;

    /** 已批阅主观题数 */
    private Integer reviewedCount;

    /** 是否全部批阅完成（等价于 status=2） */
    private Boolean fullyReviewed;

    /** 是否下发参考答案与解析（考试已结束才为 true） */
    private Boolean answerVisible;

    /** 答案可见性说明，前端直接展示 */
    private String answerTip;

    /** 逐题作答明细（按试卷题目顺序） */
    private List<QuestionItem> questions;

    /**
     * 单题作答明细
     */
    @Data
    public static class QuestionItem {

        private Long questionId;

        /** 题型：1单选 2多选 3判断 4填空 5简答 6其他主观 */
        private Integer questionType;

        private String content;

        /** 选项 JSON 数组，如 ["选项一","选项二"]，仅选择/多选题有值 */
        private String options;

        /** 该题在本试卷中的满分 */
        private BigDecimal score;

        private Integer sortOrder;

        /** 我的作答（选择题是字母，如 "A" / "A,C"；判断题 "T"/"F"；填空简答是文本） */
        private String userAnswer;

        /** 1=对 0=错；主观题未批阅时为 null */
        private Integer isCorrect;

        /** 该题得分；主观题未批阅时为 null（前端展示「待批阅」） */
        private BigDecimal getScore;

        /** 教师评语（优先阅卷评语，兜底旧字段） */
        private String teacherComment;

        /** 0=主观题 1=客观题 */
        private Integer isObjective;

        /** 阅卷状态：0=待阅卷 1=已阅卷（客观题恒为 1） */
        private Integer reviewStatus;

        /** 是否作答 */
        private Boolean answered;

        /** 参考答案（仅 answerVisible=true 时下发，否则为 null） */
        private String correctAnswer;

        /** 答案解析（仅 answerVisible=true 时下发，否则为 null） */
        private String analysis;
    }
}
