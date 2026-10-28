package com.exam.exam_system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 可统计考试列表项
 */
@Data
public class StatsExamVO {

    private Long id;
    private String examName;

    /** 学科 ID（前端课程联动下拉用） */
    private Long courseId;
    private String courseName;

    /** 考试结束时间（前端时间范围筛选用） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private String creatorName;
    private Integer studentCount;    // 参考人数
    private Integer completedCount;  // 已批阅人数
}
