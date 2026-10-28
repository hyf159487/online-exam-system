package com.exam.exam_system.dto;

import lombok.Data;

/**
 * 作业附件上传结果（文件型作业）
 *
 * <p>上传接口只负责把文件落盘并返回元数据，保存作业时前端把本对象
 * 回传到 /api/practice/paper/save，由 Service 写入 practice_paper。
 */
@Data
public class PracticeAttachment {

    /** 附件原始文件名 */
    private String fileName;

    /** 附件访问路径（形如 /uploads/homework/yyyyMM/xxx.docx） */
    private String filePath;

    /** 附件字节数 */
    private Long fileSize;

    /** 可读的文件大小文本，如 1.2 MB */
    private String fileSizeText;

    /** 附件类型归类：pdf/word/ppt/excel/text/compress/image */
    private String fileType;
}
