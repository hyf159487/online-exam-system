package com.exam.exam_system.dto;

import com.exam.exam_system.entity.ExamPaper;
import lombok.Data;

import java.util.List;

/**
 * 试卷详情 DTO：试卷基本信息 + 已选题目列表
 */
@Data
public class PaperDetail {

    private ExamPaper paper;

    private List<PaperQuestionItem> questions;
}
