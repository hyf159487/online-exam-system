package com.exam.exam_system.dto;

import com.exam.exam_system.entity.Question;
import lombok.Data;

import java.util.List;

/**
 * Word 解析结果
 */
@Data
public class WordParseResult {
    /** 首行文本（学科/章节标题） */
    private String title;
    /** 解析出的题目列表 */
    private List<Question> questions;

    // ===== 解析报告（答辩展示用） =====

    /** 成功识别的题目数量 */
    private Integer successCount;

    /** 失败的条目数量（部分失败策略：成功入库，失败丢弃并报告） */
    private Integer failedCount;

    /** 失败条目说明（格式："第3题：题干为空" / "第8题：答案缺失"） */
    private List<String> failedReasons;
}
