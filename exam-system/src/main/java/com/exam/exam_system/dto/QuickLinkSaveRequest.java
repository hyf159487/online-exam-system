package com.exam.exam_system.dto;

import lombok.Data;

import java.util.List;

/**
 * 保存快捷入口请求体：body 形如 { "links": [ { "label": "账号管理", "path": "/students", "icon": "User" } ] }
 *
 * <p>save 接口是「全量覆盖」语义：传什么就存什么，传空列表等于清空自定义配置（前端回到系统默认入口）。
 */
@Data
public class QuickLinkSaveRequest {

    /** 入口列表，按数组顺序即为首页展示顺序 */
    private List<QuickLinkItem> links;
}
