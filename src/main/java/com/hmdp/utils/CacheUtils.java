package com.hmdp.utils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.hmdp.config.ExecutorServiceConfig;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.ShopType;

import cn.hutool.core.util.BooleanUtil;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.json.JSONUtil;

@Slf4j
@Component
public class CacheUtils {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ExecutorServiceConfig executorServiceConfig;

    // ================== 互斥锁 ==================
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    // ================== 解锁互斥锁 ==================
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

     // ================== 逻辑过期缓存 ==================
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //缓存工具类
    //1.解决缓存击穿问题---互斥锁+空值穿透处理---保证数据一致性
    /**
     * 需要传递的参数
     * 1.缓存数据主键id id类型由调用传递参数，适配不同数据的不同基本数据类型的id
     * 2.制作redis中key的前言，用于区分不同的缓存数据
     * 3.缓存数据的过期时间 TTL 时间的单位 TimeUnit
     * 4.前往数据库中根据主键id查询的mybatisplus方法
     * 5.查询返回的数据类型
     */
    public <ID, R> R queryWithMutex(
        String keyPrefix,
        ID id,
        Class<R> type,
        Function<ID, R> dbFallback,
        Long time,
        TimeUnit unit
    ){
        String key = keyPrefix + id;

        // 1. 查缓存
        Object cacheValue = stringRedisTemplate.opsForValue().get(key);

        // 2. 命中
        if (cacheValue != null) {
            // 空值处理
            if ("".equals(cacheValue)) {
                return null;
            }
            return type.cast(cacheValue);
        }

        // 3. 构建锁key
        String lockKey = "lock:" + key;
        R r = null;

        try {
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }

            // 4. 查数据库（关键点：函数式接口）
            r = dbFallback.apply(id);

            // 5. 不存在 → 写空值
            if (r == null) {
                stringRedisTemplate.opsForValue()
                    .set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 6. 写缓存
            setWithLogicalExpire(key, JSONUtil.toJsonStr(r), time, unit);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }

        return r;
    }
    //2.解决热点key缓存击穿问题---逻辑过期---避免缓存击穿问题
    public <ID, R> R queryWithLogicalExpire(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit unit
        )
        {
        String key = keyPrefix + id;

        Object obj = stringRedisTemplate.opsForValue().get(key);
        if (obj == null) {
            return null;
        }
        // 解析JSON字符串
        obj = JSONUtil.toBean((String) obj, RedisData.class);

        RedisData redisData = (RedisData) obj;
        R data = type.cast(redisData.getData());
        LocalDateTime expireTime = redisData.getExpireTime();

        // ✅ 未过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            return data;
        }

        // ❗ 已过期 → 尝试重建缓存
        String lockKey = "lock:" + key;
        boolean isLock = tryLock(lockKey);

        if (isLock) {
            executorServiceConfig.executorService().execute(() -> {
                try {
                    R freshData = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, freshData, time, unit);
                } catch (Exception e) {
                    log.error("缓存重建失败", e);
                } finally {
                    unlock(lockKey);
                }
            });
        }

        // ⚠️ 返回旧数据
        return data;
    }

    public List<ShopType> get(String key, Class<ShopType> class1) {
        return JSONUtil.toList(JSONUtil.toJsonStr(stringRedisTemplate.opsForValue().get(key)), class1);
    }

}
