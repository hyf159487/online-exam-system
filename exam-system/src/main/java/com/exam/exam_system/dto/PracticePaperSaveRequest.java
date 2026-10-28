package com.exam.exam_system.dto;

import com.exam.exam_system.entity.PracticePaperQuestion;
import lombok.Data;

import java.util.List;

/**
 * 保存练习卷请求
 */
@Data
public class PracticePaperSaveRequest {

    /** 有ID=编辑，无ID=新增 */
    private Long id;

    private String paperName;

    private Long courseId;

    private String description;

    /** 保存时的目标状态：0草稿 1已发布；默认0草稿 */
    private Integer targetStatus;

    /** 题目集合（含 questionId / score / sortNo）；文件型作业传空集合 */
    private List<PracticePaperQuestion> questions;

    // ===== 文件型作业：作业内容为一份附件文件 =====

    /** 附件原始文件名（由 /api/practice/paper/upload 返回后回传） */
    private String attachmentName;

    /** 附件访问路径（由 /api/practice/paper/upload 返回后回传） */
    private String attachmentPath;

    /** 附件字节数 */
    private Long attachmentSize;

    /** 附件类型归类：pdf/word/ppt/excel/text/compress/image */
    private String attachmentType;
}
