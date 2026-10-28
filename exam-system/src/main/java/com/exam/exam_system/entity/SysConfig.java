package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统参数配置实体，对应 sys_config 表
 *
 * <p>业务含义：全局键值对配置，修改后全系统生效。
 * <p>参数由初始化脚本预置，只允许修改值，不允许新增/删除。
 *
 * <p>config_type 决定前端渲染组件：
 * <ul>
 *   <li>number  — el-input-number 数字输入框</li>
 *   <li>boolean — el-switch 开关</li>
 *   <li>string  — el-input 文本输入框</li>
 * </ul>
 */
@Data
public class SysConfig {

    private Long id;

    /** 参数名称（中文，页面展示用） */
    private String configName;

    /** 参数编码 key（业务代码按此读取，唯一） */
    private String configKey;

    /** 参数值（统一存字符串，数字存 "24"，开关存 "true"/"false"） */
    private String configValue;

    /** 参数类型：number / boolean / string */
    private String configType;

    /** 参数说明 */
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 便捷方法：按类型转换 configValue
     */
    public Integer getValueAsInt() {
        try {
            return Integer.parseInt(configValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Long getValueAsLong() {
        try {
            return Long.parseLong(configValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Boolean getValueAsBoolean() {
        return "true".equalsIgnoreCase(configValue);
    }
}
