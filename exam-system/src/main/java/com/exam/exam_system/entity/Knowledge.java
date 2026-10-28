package com.exam.exam_system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识点实体，对应 knowledge 表
 */
@Data
public class Knowledge {

    private Long id;

    private String knowledgeName;

    private Long courseId;

    private String description;

    private LocalDateTime createTime;
}
