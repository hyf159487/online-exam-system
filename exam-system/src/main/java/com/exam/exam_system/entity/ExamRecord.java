package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试记录实体，对应 exam_record 表（学生一次交卷 = 一条记录）
 *
 * <p>status：0=考试中（未交卷） 1=已交卷 2=已判分
 */
@Data
public class ExamRecord {

    private Long id;

    /** 试卷 ID */
    private Long paperId;

    /** 考试 ID（关联 exam 表） */
    private Long examId;

    /** 学生用户 ID */
    private Long userId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submitTime;

    /** 得分 */
    private BigDecimal totalScore;

    /** 0=考试中 1=已交卷 2=已判分 */
    private Integer status;

    /** 1=及格 0=不及格 */
    private Integer isPass;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // ========== 成绩快照字段（表字段，交卷时冗余）==========
    // 避免"考试/试卷被删除后，学生成绩联查不到导致信息丢失"
    // 查询时优先读快照，联表仅作兜底（COALESCE）

    /** 试卷名快照 */
    private String paperName;

    /** 试卷满分快照 */
    private BigDecimal paperTotalScore;

    /** 及格分快照 */
    private BigDecimal passScore;

    // ========== 以下为非表字段，联表回显用 ==========

    /** 学号（联 sys_user） */
    private String username;

    /** 学生姓名（联 sys_user） */
    private String realName;

    /** 学科名称（联 sys_course，非表字段） */
    private String courseName;

    /**
     * 学生能否回看这份试卷（非表字段，1=可以 0=不可以）
     *
     * <p>口径与 Service 层一致：关联考试已过结束时间（或没有关联考试）才可回看。
     * 考试进行中不放行——同一场考试里还有同学在作答，题目与答案都不能提前流出去。</p>
     */
    private Integer canViewPaper;

    /**
     * 排名（非表字段）
     * <p>由 SQL 窗口函数 {@code RANK() OVER (PARTITION BY paper_id ORDER BY total_score DESC)} 计算</p>
     * <p>同卷内按分数从高到低排名，同分同名次；不同卷之间互不干扰（跨卷比原始分无意义）</p>
     */
    private Integer rank;
}
