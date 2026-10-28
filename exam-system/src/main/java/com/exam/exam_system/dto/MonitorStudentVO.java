package com.exam.exam_system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 考试监控-学生状态 VO（联表查询结果，含学生信息）
 */
@Data
public class MonitorStudentVO {

    private Long id;
    private Long examId;
    private Long studentId;

    /** 0未开始 1考试中 2已交卷 3强制交卷 */
    private Integer status;

    private Integer answeredCount;
    private Integer totalCount;
    private Integer remainingTime;
    private Integer screenSwitchCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime enterTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime leaveTime;

    // ========== 联表回显字段 ==========

    /** 学生学号 */
    private String username;

    /** 学生姓名 */
    private String realName;

    /** 班级名称 */
    private String className;

    /** 班级ID */
    private Long classId;

    /** 答题进度百分比 */
    public Integer getProgressPercent() {
        if (totalCount == null || totalCount == 0 || answeredCount == null) return 0;
        return Math.min(100, (int) Math.round(answeredCount * 100.0 / totalCount));
    }

    /** 剩余时间格式化（HH:mm:ss） */
    public String getRemainingTimeText() {
        if (remainingTime == null || remainingTime <= 0) return "00:00:00";
        int h = remainingTime / 3600;
        int m = (remainingTime % 3600) / 60;
        int s = remainingTime % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}
