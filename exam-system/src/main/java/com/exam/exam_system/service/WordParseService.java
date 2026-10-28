package com.exam.exam_system.service;

import com.exam.exam_system.dto.WordParseResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * Word 文档解析服务
 * <p>
 * 格式约定（参见 woldtemplate.docx）：
 * 1. 第一行为试卷名称（解析时忽略，仅用于识别文档类型）
 * 2. 题目以 "数字." 开头，例如 "1.等腰直角三角形..."
 * 3. 选项以字母+标点开头，例如 "A. 1"、"B. 2"
 * 4. 答案通常标注在题干中，格式为 "（X ）" 或 "(X )"
 * 5. "解析：" 开头的段落为解析
 * 6. 图片嵌入在题干或选项段落中（通过关系 ID 关联媒体文件）
 * 7. 支持单选（一个正确选项）/多选（多个正确选项）/判断/填空/简答
 */
public interface WordParseService {

    /**
     * 解析上传的 Word 文档
     *
     * @param file       上传的 .docx 文件
     * @param courseId   学科 ID（可选）
     * @param creatorId  创建人 ID
     * @return 解析结果（包含首行标题和题目列表）
     */
    WordParseResult parseWord(MultipartFile file, Long courseId, Long creatorId);

    /**
     * 解析粘贴的纯文本（从 Word 复制过来的文本）
     *
     * @param text       纯文本内容
     * @param courseId   学科 ID（可选）
     * @param creatorId  创建人 ID
     * @return 解析结果（包含首行标题和题目列表）
     */
    WordParseResult parseText(String text, Long courseId, Long creatorId);
}
