package com.exam.exam_system.dto;

import lombok.Data;

import java.util.List;

/**
 * 发布通知请求 DTO
 */
@Data
public class NoticeSendRequest {

    /** 通知标题 */
    private String title;

    /** 通知正文 */
    private String content;

    /** 接收对象：1全部学生 2指定班级 3指定考试考生 */
    private Integer receiveType;

    /** 指定班级ID集合（receiveType=2 时使用） */
    private List<Long> targetClassIds;

    /** 指定考试ID（receiveType=3 时使用） */
    private Long targetExamId;
}
