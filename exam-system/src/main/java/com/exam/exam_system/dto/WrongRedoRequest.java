package com.exam.exam_system.dto;

import lombok.Data;

/** 错题本重做：提交答案 */
@Data
public class WrongRedoRequest {
    /** 学生重做时提交的答案 */
    private String userAnswer;
}
