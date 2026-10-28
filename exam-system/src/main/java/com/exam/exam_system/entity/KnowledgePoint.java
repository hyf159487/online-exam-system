package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识点实体，对应 knowledge_point 表
 *
 * <p>知识点归属课程：一门课程下有多条知识点，供试题绑定（question.knowledge_id）、
 * 班级学情分析「知识点正确率」与试题质量分析使用。
 */
@Data
public class KnowledgePoint {

    /** 知识点 ID */
    private Long id;

    /** 所属课程 ID（sys_course.id） */
    private Long courseId;

    /** 知识点名称 */
    private String pointName;

    /** 知识点编码 */
    private String knowledgeCode;

    /** 父知识点 ID（0=顶级；保留层级字段兼容历史数据） */
    private Long parentId;

    /** 排序号：越小越靠前（题目录入下拉按此排序） */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 所属课程名称（联查展示用，非表字段） */
    private String courseName;
}
