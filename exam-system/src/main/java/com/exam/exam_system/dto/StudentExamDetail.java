package com.exam.exam_system.dto;

import com.exam.exam_system.entity.Exam;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生端考试详情 DTO：考试信息 + 题目列表（不含答案）
 *
 * <p>startTime / deadline 是【个人作答时间】，由后端基于考试记录计算：
 * <ul>
 *     <li>startTime：本次进入考试的真实开考时间（刷新页面不变）</li>
 *     <li>deadline：个人作答截止时间 = min(开考时间 + 考试时长, 考试整体结束时间)</li>
 * </ul>
 *
 * <p>answers 是【已保存的答题进度】（刷新页面 / 断线重连后恢复作答）
 */
@Data
public class StudentExamDetail {

    private Exam exam;

    private List<StudentExamQuestion> questions;

    /** 已保存的答题进度：questionId -> 作答内容（与 submit 的 answers 格式一致） */
    private List<AnswerItem> answers;

    /** 个人开考时间（首次进入考试时由后端记录） */
    private LocalDateTime startTime;

    /** 个人作答截止时间（前端倒计时以此为准，刷新不重置） */
    private LocalDateTime deadline;

    /** 考试时长（分钟），与 exam.duration 一致 */
    private Integer duration;

    /** 考试超时自动交卷时间（分钟），来自系统参数 exam_timeout_minutes */
    private Integer examTimeoutMinutes;

    /** 防作弊开关，来自系统参数 anti_cheat_enabled */
    private Boolean antiCheatEnabled;

    /** 已累计切屏次数（刷新页面后恢复显示，数据来自 exam_student.screen_switch_count） */
    private Integer screenSwitchCount;

    /**
     * 单题作答进度（进度保存 / 进度恢复用）
     */
    @Data
    public static class AnswerItem {
        private Long questionId;
        private String answer;
    }
}
