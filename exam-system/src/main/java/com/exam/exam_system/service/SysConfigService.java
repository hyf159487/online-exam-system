package com.exam.exam_system.service;

import com.exam.exam_system.entity.SysConfig;

import java.util.List;

/**
 * 系统参数配置服务
 *
 * <p>核心方法：
 * <ul>
 *   <li>{@link #list}                    全部参数列表（管理页面用）</li>
 *   <li>{@link #batchUpdate}             批量更新参数值（管理页面保存用，带缓存刷新）</li>
 *   <li>{@link #getValue}                按 key 取字符串值（带缓存）</li>
 *   <li>{@link #getInt}                  按 key 取整数值（带缓存）</li>
 *   <li>{@link #getLong}                 按 key 取长整型值（带缓存）</li>
 *   <li>{@link #getBoolean}              按 key 取布尔值（带缓存）</li>
 *   <li>{@link #refreshCache}            强制刷新缓存</li>
 * </ul>
 *
 * <p>缓存策略：ConcurrentHashMap 内存缓存，启动时加载，更新时刷新。
 * 避免每次登录/考试/列表都查数据库。
 */
public interface SysConfigService {

    List<SysConfig> list();

    /**
     * 批量更新参数值
     * @param list 只需要 id 和 configValue 字段
     */
    void batchUpdate(List<SysConfig> list);

    /** 按 key 取字符串值（带缓存） */
    String getValue(String key);

    /** 按 key 取整数值（带缓存），不存在或格式错误返回 defaultValue */
    int getInt(String key, int defaultValue);

    /** 按 key 取长整型值（带缓存），不存在或格式错误返回 defaultValue */
    long getLong(String key, long defaultValue);

    /** 按 key 取布尔值（带缓存），不存在返回 defaultValue */
    boolean getBoolean(String key, boolean defaultValue);

    /** 强制刷新缓存（批量更新后自动调用） */
    void refreshCache();
}
