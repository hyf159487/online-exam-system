package com.exam.exam_system.service.impl;

import com.exam.exam_system.dto.QuickLinkItem;
import com.exam.exam_system.entity.UserQuickLink;
import com.exam.exam_system.mapper.UserQuickLinkMapper;
import com.exam.exam_system.service.UserQuickLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户自定义快捷入口服务实现。
 *
 * <p>写入口的规则统一收在这里，Controller 只做转发：
 * 去空白 → 补路由前导 / → 按路由去重 → 截断超长字段 → 限制条数上限，
 * 保证落库数据干净（首页是纯展示，脏数据只会让页面出现点不动的按钮）。
 */
@Service
public class UserQuickLinkServiceImpl implements UserQuickLinkService {

    /** 单用户最多保存的入口数：防止异常数据把首页撑爆 */
    private static final int MAX_LINKS = 20;

    /** 名称 / 路由 / 图标的最大长度，与 user_quick_link 表结构对齐 */
    private static final int MAX_LABEL_LEN = 50;
    private static final int MAX_PATH_LEN = 200;
    private static final int MAX_ICON_LEN = 50;

    @Autowired
    private UserQuickLinkMapper userQuickLinkMapper;

    @Override
    public List<UserQuickLink> listByUser(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<UserQuickLink> list = userQuickLinkMapper.listByUser(userId);
        return list == null ? Collections.emptyList() : list;
    }

    @Override
    @Transactional
    public void save(Long userId, List<QuickLinkItem> links) {
        if (userId == null) {
            return;
        }
        List<QuickLinkItem> clean = normalize(links);
        // 全量覆盖：先清空再插入，避免旧入口残留导致首页出现已删掉的按钮
        userQuickLinkMapper.deleteByUser(userId);
        if (!clean.isEmpty()) {
            userQuickLinkMapper.batchInsert(userId, clean);
        }
    }

    @Override
    public void reset(Long userId) {
        if (userId == null) {
            return;
        }
        userQuickLinkMapper.deleteByUser(userId);
    }

    /** 清洗入口列表：过滤非法项 + 路由去重 + 截断超长字段 + 限制条数 */
    private List<QuickLinkItem> normalize(List<QuickLinkItem> links) {
        if (links == null || links.isEmpty()) {
            return Collections.emptyList();
        }
        List<QuickLinkItem> clean = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        for (QuickLinkItem item : links) {
            if (item == null) {
                continue;
            }
            String label = trimToLength(item.getLabel(), MAX_LABEL_LEN);
            String path = trimToLength(item.getPath(), MAX_PATH_LEN);
            // 名称与路由缺一不可：缺了前端渲染出来的按钮既点不动也没法看
            if (label == null || path == null) {
                continue;
            }
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            if (!paths.add(path)) {
                continue;   // 同一路由只保留第一条
            }
            QuickLinkItem one = new QuickLinkItem();
            one.setLabel(label);
            one.setPath(path);
            one.setIcon(trimToLength(item.getIcon(), MAX_ICON_LEN));
            clean.add(one);
            if (clean.size() >= MAX_LINKS) {
                break;
            }
        }
        return clean;
    }

    /** 去首尾空白；空串归一为 null；超长按上限截断 */
    private String trimToLength(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
