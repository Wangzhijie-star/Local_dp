package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 开始时间戳（2024-01-01 00:00:00）
     * 用于计算相对时间戳，减少位数
     */
    private static final long BEGIN_TIMESTAMP = 1704067200L;

    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;

    /**
     * 生成全局唯一ID
     * 格式：符号位(1bit) + 时间戳(31bit) + 序列号(32bit)
     *
     * @param keyPrefix 业务前缀，用于区分不同业务的ID
     * @return 全局唯一ID
     */
    public long nextId(String keyPrefix) {
        // 1. 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2. 生成序列号（使用Redis自增）
        // 2.1 获取当前日期，精确到天，用于构建Redis的key
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2 自增长，key格式：icr:{keyPrefix}:{date}，初始为0，在redis中可以查询count值
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3. 拼接并返回
        // 时间戳左移32位，腾出低32位给序列号
        // 再与序列号进行或运算，将序列号填充到低32位
        return timestamp << COUNT_BITS | count;
    }

    /**
     * 生成带业务标识的字符串ID
     * 格式：{prefix}{timestamp}{序列号}
     *
     * @param prefix 业务前缀，如 ORDER、USER 等
     * @return 字符串类型的ID
     */
    public String nextIdStr(String prefix) {
        long id = nextId(prefix.toLowerCase());
        return prefix + id;
    }

    /**
     * 批量生成ID
     *
     * @param keyPrefix 业务前缀
     * @param count     生成数量
     * @return ID数组
     */
    public long[] nextIds(String keyPrefix, int count) {
        long[] ids = new long[count];
        for (int i = 0; i < count; i++) {
            ids[i] = nextId(keyPrefix);
        }
        return ids;
    }
}
