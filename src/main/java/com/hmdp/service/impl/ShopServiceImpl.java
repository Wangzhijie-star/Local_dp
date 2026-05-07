package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.ExecutorServiceConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheUtils;
import com.hmdp.utils.RedisConstants;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import cn.hutool.json.JSONUtil;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    // ================== 逻辑过期缓存 ==================
    @Autowired
    private CacheUtils cacheUtils;
    //注入redis模板
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    //建立线程池，用于处理查询数据库的任务
    @Autowired
    private ExecutorServiceConfig executorServiceConfig;
    //注入objectMapper，用于序列化和反序列化对象

    @Override
    public Result queryById(Long id) {
        //缓存预热
        // preheatCache(id);
        return Result.ok(cacheUtils.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES));
    }
    /**
     *  逻辑过期解决缓存击穿问题，主要针对的是热点key
     */
    public Result solveCachePenetrationByLogic(Long id) {
        //1.合成缓存key，判断缓存是否存在且是否过期
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null){
            return Result.ok(null);
        }
        RedisData cacheValue=JSONUtil.toBean(JSONUtil.parseObj(json), RedisData.class);
        //2.1缓存不存在，直接返回null
        if (!(cacheValue.getData() instanceof Shop)){
            return Result.ok(null);            
        }
        //2.2判断缓存是否过期
        if (cacheValue.getExpireTime().isAfter(LocalDateTime.now())){
            //未过期，直接返回缓存中的数据
            return Result.ok(cacheValue.getData());
        }
        //3已过期
        //3.1.尝试获取互斥锁，如果获取失败，直接返回过期redis中的信息
        String lockKey = RedisConstants.CACHE_SHOP_LOCK_KEY + id;
        boolean locked = false;
        locked = tryLock(lockKey);
        if (!locked) {
            return Result.ok(cacheValue.getData());
        }
        //3.2.使用线程池异步执行数据库查询和缓存写入操作，避免阻塞主线程
        executorServiceConfig.executorService().execute(() -> {
            try {
                     //3.2.1.查询数据库
                    Shop shop = getById(id);
                    RedisData redisData = new RedisData();
                    //3.2.2.写入缓存
                    redisData.setData(shop);
                    redisData.setExpireTime(LocalDateTime.now().plusMinutes(RedisConstants.CACHE_SHOP_TTL));
                    //3.2.3.将缓存中的数据序列化为json字符串
                    String jsondata = JSONUtil.toJsonStr(redisData);
                    stringRedisTemplate.opsForValue().set(key, jsondata, RedisConstants.CACHE_HOT_SHOP_TTL, TimeUnit.MINUTES);
                } finally {
                    //释放互斥锁
                    unlock(lockKey);
                }
        });
        //3.3.返回缓存中的旧数据
        return Result.ok(cacheValue.getData());
        
    }
    //缓存预热
    private void preheatCache(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        RedisData redisData = new RedisData();
        redisData.setData(getById(id));
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(RedisConstants.CACHE_SHOP_TTL));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData), RedisConstants.CACHE_HOT_SHOP_TTL, TimeUnit.MINUTES);
    }

    /**
     * 解决缓存击穿问题
     */
    public Result solveCachePunch(Long id) {
        //1.合成缓存key，判断缓存是否存在且是否过期
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        Object cacheValue = stringRedisTemplate.opsForValue().get(key);
        //2.1缓存存在，直接返回缓存中的数据
        if (cacheValue instanceof Shop) {
            return Result.ok(cacheValue);
        }
        //2.2缓存为空，直接返回商铺不存在
        if ("".equals(cacheValue)) {
            return Result.fail("商铺不存在");
        }
        //3.1.尝试获取互斥锁，如果获取失败，直接返回缓存中的信息
        String lockKey = RedisConstants.CACHE_SHOP_LOCK_KEY + id;
        boolean locked = false;
        try {
            //3.1.1.获取互斥锁
            locked = tryLock(lockKey);
            //3.1.2.如果获取互斥锁失败，等待50毫秒后重查缓存
            if (!locked) {
                Thread.sleep(50);
                return solveCachePunch(id);
            }
            //3.1.3.查询数据库
            Shop shop = getById(id);
            //3.1.4.如果数据库中不存在商铺，写入空字符串到缓存
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("商铺不存在");
            }
            //3.1.5.如果数据库中存在商铺，写入缓存
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            //3.1.6.返回缓存中的新数据
            return Result.ok(shop);
        } catch (InterruptedException e) {
            //3.1.7.如果查询商铺时线程被中断，释放互斥锁
            Thread.currentThread().interrupt();
            //3.1.8.抛出异常，由调用者处理
            log.error("查询商铺时线程被中断", e);
            throw new RuntimeException("查询商铺时线程被中断", e);
        } finally {
            //3.1.9.释放互斥锁
            if (locked) {
                //3.1.9.1.释放互斥锁
                unlock(lockKey);
            }
        }
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
    
    // public Result solveCachePenetration(Long id) {
    //     String key = RedisConstants.CACHE_SHOP_KEY + id;
    //     Object cacheValue = stringRedisTemplate.opsForValue().get(key);
    //     if (cacheValue instanceof Shop) {
    //         return Result.ok(cacheValue);
    //     }
    //     if ("".equals(cacheValue)) {
    //         return Result.fail("商铺不存在");
    //     }

    //     Shop shop = getById(id);
    //     if (shop == null) {
    //         stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
    //         return Result.fail("商铺不存在");
    //     }

    //     stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
    //     return Result.ok(shop);
    // }

    @Override
    public void update(Shop shop) {
        if (shop.getId() == null) {
            throw new IllegalArgumentException("商铺id不能为空");
        }
        updateById(shop);
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
    }
}
