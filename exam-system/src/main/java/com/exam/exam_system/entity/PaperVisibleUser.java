package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 试卷可见教师中间表实体，对应 exam_paper_visible_user
 *
 * <p>多对多关系：一张试卷可共享给多个教师；一个教师可被共享多张试卷。
 * 创建人本身不存入此表（creator_id 永远拥有全部权限）。
 */
@Data
public class PaperVisibleUser {

    private Long id;
    private Long paperId;
    private Long userId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
