package com.exam.exam_system.service;

import com.exam.exam_system.dto.QuickLinkItem;
import com.exam.exam_system.entity.UserQuickLink;

import java.util.List;

/**
 * 用户自定义快捷入口服务：首页「快捷入口」区的个人配置，跟账号走。
 */
public interface UserQuickLinkService {

    /**
     * 当前用户的自定义入口。
     * @return 未配置过时返回空列表（前端回落到系统默认入口）
     */
    List<UserQuickLink> listByUser(Long userId);

    /**
     * 全量保存当前用户的快捷入口（先清空再写入）。
     * @param links 前端提交的入口列表；传 null / 空列表等于清空自定义配置
     */
    void save(Long userId, List<QuickLinkItem> links);

    /** 恢复默认：清空当前用户的自定义配置 */
    void reset(Long userId);
}
