package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典类型实体，对应 sys_dict_type 表
 *
 * <p>业务含义：定义一组下拉选项的分类，如「题型」「难度」「考试状态」。
 * <p>is_builtin=1 为系统内置字典，禁止删除，防止业务页面下拉框为空。
 */
@Data
public class SysDictType {

    private Long id;

    /** 字典类型名称：题型 / 难度 / 考试状态 */
    private String typeName;

    /** 字典编码：question_type / difficulty / exam_status，前端按此编码拉选项 */
    private String typeCode;

    /** 备注 */
    private String remark;

    /** 是否系统内置：1=是 0=否（内置禁止删除） */
    private Integer isBuiltin;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
