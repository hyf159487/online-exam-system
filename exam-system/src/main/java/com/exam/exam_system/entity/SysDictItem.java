package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典子项实体，对应 sys_dict_item 表
 *
 * <p>业务含义：某个字典类型下的具体下拉选项。
 * <p>示例：题型(question_type) 下的「单选题(value=1)」「多选题(value=2)」等。
 */
@Data
public class SysDictItem {

    private Long id;

    /** 字典类型ID */
    private Long typeId;

    /** 显示标签（下拉框显示的文本） */
    private String itemLabel;

    /** 存储值（下拉框提交的值） */
    private String itemValue;

    /** 排序号（升序） */
    private Integer sortOrder;

    /** 是否启用：1=启用 0=禁用 */
    private Integer enabled;

    /** 备注 */
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
