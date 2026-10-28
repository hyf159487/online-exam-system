package com.exam.exam_system.service;

import com.exam.exam_system.dto.WordParseResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 统一文件解析服务
 * <p>
 * 支持格式：
 * 1. Word (.docx) - 使用 WordParseService
 * 2. Excel (.xlsx) - 使用 Apache POI 解析表格
 * 3. PDF (.pdf) - 使用 Apache PDFBox 提取文本
 * 4. JSON (.json) - 直接解析 JSON 数组
 * 5. 图片 (.png/.jpg/.jpeg) - 返回提示需 OCR
 */
public interface FileParseService {

    /**
     * 根据文件扩展名自动选择解析器并解析
     *
     * @param file      上传文件
     * @param courseId  学科ID（可选）
     * @param creatorId 创建人ID
     * @return 解析结果（含标题+题目列表）
     */
    WordParseResult parseFile(MultipartFile file, Long courseId, Long creatorId);

    /**
     * 解析 JSON 格式内容
     *
     * @param jsonContent JSON 字符串
     * @param courseId    学科ID（可选）
     * @param creatorId   创建人ID
     * @return 解析结果
     */
    WordParseResult parseJson(String jsonContent, Long courseId, Long creatorId);
}
