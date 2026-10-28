package com.exam.exam_system.dto;

import lombok.Data;

/**
 * 快捷入口条目：前端保存时提交的最小结构。
 *
 * <p>字段名与前端首页 quickLinks 里的对象保持一致（label / path / icon），
 * 落库时再映射到 user_quick_link 表的 label / route_path / icon。
 */
@Data
public class QuickLinkItem {

    /** 入口名称，如「待审核试题」 */
    private String label;

    /** 路由地址，如 /audit/pending（不带前导 / 时后端会自动补上） */
    private String path;

    /** 图标名（可选，Element Plus 图标组件名，如 Checked / Files） */
    private String icon;
}
