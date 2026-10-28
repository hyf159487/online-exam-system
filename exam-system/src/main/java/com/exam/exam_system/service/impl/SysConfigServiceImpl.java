package com.exam.exam_system.service.impl;

import com.exam.exam_system.entity.SysConfig;
import com.exam.exam_system.mapper.SysConfigMapper;
import com.exam.exam_system.service.SysConfigService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统参数配置服务实现
 *
 * <p>缓存策略：
 * <ul>
 *   <li>@PostConstruct 时从 DB 全量加载到 ConcurrentHashMap</li>
 *   <li>业务代码按 key 取值时直接查内存，零 DB 开销</li>
 *   <li>批量更新后自动刷新缓存</li>
 * </ul>
 *
 * <p>线程安全：ConcurrentHashMap 保证多线程读写安全。
 */
@Service
public class SysConfigServiceImpl implements SysConfigService {

    @Autowired
    private SysConfigMapper sysConfigMapper;

    /** 内存缓存：key=configKey, value=SysConfig 实体 */
    private final ConcurrentHashMap<String, SysConfig> cache = new ConcurrentHashMap<>();

    /** 启动时加载全部配置到缓存 */
    @PostConstruct
    public void init() {
        loadAllToCache();
    }

    private void loadAllToCache() {
        List<SysConfig> list = sysConfigMapper.selectAll();
        cache.clear();
        for (SysConfig c : list) {
            cache.put(c.getConfigKey(), c);
        }
    }

    @Override
    public List<SysConfig> list() {
        return sysConfigMapper.selectAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdate(List<SysConfig> list) {
        if (list == null || list.isEmpty()) return;
        sysConfigMapper.batchUpdateValues(list);
        // 刷新缓存：重新从 DB 加载最新值
        for (SysConfig item : list) {
            SysConfig refreshed = sysConfigMapper.selectById(item.getId());
            if (refreshed != null) {
                cache.put(refreshed.getConfigKey(), refreshed);
            }
        }
    }

    @Override
    public String getValue(String key) {
        SysConfig c = cache.get(key);
        return c != null ? c.getConfigValue() : null;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        SysConfig c = cache.get(key);
        if (c == null) return defaultValue;
        try {
            return Integer.parseInt(c.getConfigValue());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public long getLong(String key, long defaultValue) {
        SysConfig c = cache.get(key);
        if (c == null) return defaultValue;
        try {
            return Long.parseLong(c.getConfigValue());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        SysConfig c = cache.get(key);
        if (c == null) return defaultValue;
        return "true".equalsIgnoreCase(c.getConfigValue());
    }

    @Override
    public void refreshCache() {
        loadAllToCache();
    }
}
