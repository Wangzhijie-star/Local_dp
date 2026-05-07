package com.hmdp.service.impl;

import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.extern.slf4j.Slf4j;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public List<ShopType> list() {
        //设置redis key
        String key = RedisConstants.SHOP_TYPE_LIST;
        //先查缓存
        String cacheJson = stringRedisTemplate.opsForValue().get(key);
        if (cacheJson != null && !cacheJson.isEmpty()) {
            // 缓存中有数据，直接返回
            log.info("cache hit: {}", key);
            return JSONUtil.toList(cacheJson, ShopType.class);
        }
        //if没有去数据库查询
        List<ShopType> typeList = baseMapper.selectList();
        log.info("select all shop type: {}", typeList);
        //redis缓存查库结果
        if (typeList != null && !typeList.isEmpty()) {
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList), RedisConstants.SHOP_TYPE_LIST_TTL, TimeUnit.MINUTES);
        }
        //返回查库结果
        return typeList;
    }
}
