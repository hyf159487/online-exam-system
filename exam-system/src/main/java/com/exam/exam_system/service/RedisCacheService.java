package com.exam.exam_system.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis 缓存服务（带优雅降级）
 *
 * <p>所有缓存操作均包裹 try-catch：本机未安装 Redis 或 Redis 服务不可用时，
 * 缓存操作自动跳过，业务功能不受影响。为避免频繁重试，连续失败后
 * 会短暂标记 Redis 不可用，到期后再自动恢复探测。</p>
 */
@Service
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    /** 失败熔断时间：Redis 连续失败后，间隔多久再尝试 */
    private static final long DEGRADE_RETRY_MS = 60_000L;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /** JSON 序列化器（Spring Boot 4 内置 Jackson 3） */
    @Autowired(required = false)
    private ObjectMapper objectMapper;

    /** Redis 是否可用（降级状态缓存，避免每次操作都抛异常） */
    private final AtomicBoolean available = new AtomicBoolean(true);

    /** 熔断截止时间戳 */
    private volatile long degradeUntil = 0L;

    /** 本地兜底缓存：Redis 不可用时退化为进程内缓存（可选启用） */
    private final ConcurrentMap<String, String> localCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> localExpire = new ConcurrentHashMap<>();

    private boolean ready() {
        if (redisTemplate == null) {
            return false;
        }
        if (!available.get()) {
            // 熔断期内不重试
            if (System.currentTimeMillis() < degradeUntil) {
                return false;
            }
            // 到期后恢复探测
            available.set(true);
        }
        return true;
    }

    private void markDegraded(String op, Exception e) {
        if (available.getAndSet(false)) {
            log.warn("Redis 不可用，缓存操作已降级（op={}）：{}", op, e.getMessage());
        }
        degradeUntil = System.currentTimeMillis() + DEGRADE_RETRY_MS;
    }

    /**
     * 写入字符串缓存
     */
    public void set(String key, String value, long ttlSeconds) {
        if (!ready()) {
            localSet(key, value, ttlSeconds);
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            markDegraded("set", e);
            localSet(key, value, ttlSeconds);
        }
    }

    /**
     * 读取字符串缓存，未命中返回 null
     */
    public String get(String key) {
        if (!ready()) {
            return localGet(key);
        }
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return value;
            }
        } catch (Exception e) {
            markDegraded("get", e);
        }
        return localGet(key);
    }

    /**
     * 删除缓存
     */
    public void delete(String key) {
        localDelete(key);
        if (!ready()) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            markDegraded("delete", e);
        }
    }

    /**
     * 是否存在该 key
     */
    public boolean hasKey(String key) {
        if (!ready()) {
            Long expire = localExpire.get(key);
            return expire != null && expire > System.currentTimeMillis();
        }
        try {
            Boolean exists = redisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(exists)) {
                return true;
            }
        } catch (Exception e) {
            markDegraded("hasKey", e);
        }
        Long expire = localExpire.get(key);
        return expire != null && expire > System.currentTimeMillis();
    }

    // ==================== JSON 对象缓存 ====================

    /**
     * 写入对象缓存（自动序列化为 JSON）
     */
    public void setObject(String key, Object value, long ttlSeconds) {
        if (objectMapper == null) {
            return;
        }
        try {
            set(key, objectMapper.writeValueAsString(value), ttlSeconds);
        } catch (Exception e) {
            log.warn("对象缓存序列化失败（key={}）：{}", key, e.getMessage());
        }
    }

    /**
     * 读取对象缓存（反序列化），未命中或失败返回 null
     */
    public <T> T getObject(String key, Class<T> clazz) {
        if (objectMapper == null) {
            return null;
        }
        String json = get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("对象缓存反序列化失败（key={}）：{}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 读取对象缓存（泛型，如 List），未命中或失败返回 null
     */
    public <T> T getObject(String key, TypeReference<T> typeRef) {
        if (objectMapper == null) {
            return null;
        }
        String json = get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("对象缓存反序列化失败（key={}）：{}", key, e.getMessage());
            return null;
        }
    }

    // ==================== 本地兜底缓存 ====================

    private void localSet(String key, String value, long ttlSeconds) {
        localCache.put(key, value);
        localExpire.put(key, System.currentTimeMillis() + ttlSeconds * 1000L);
    }

    private String localGet(String key) {
        Long expire = localExpire.get(key);
        if (expire != null && expire > System.currentTimeMillis()) {
            return localCache.get(key);
        }
        localCache.remove(key);
        localExpire.remove(key);
        return null;
    }

    private void localDelete(String key) {
        localCache.remove(key);
        localExpire.remove(key);
    }
}
