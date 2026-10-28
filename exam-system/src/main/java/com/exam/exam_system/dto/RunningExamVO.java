package com.exam.exam_system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 正在进行中的考试 VO（用于下拉选择）
 */
@Data
public class RunningExamVO {

    private Long id;
    private String examName;
    private Long courseId;
    private String courseName;
    private Long paperId;
    private String paperName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Integer duration;

    /** 已进入考试的学生数 */
    private Integer studentCount;

    /** 正在考试中的学生数 */
    private Integer activeCount;
}
