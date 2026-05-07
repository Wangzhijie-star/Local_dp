package com.hmdp.service.impl;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mq.SeckillOrderProducer;

import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import com.hmdp.service.ISeckillVoucherService;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;




/**
 * 基于Redis+Lua的秒杀服务实现
 * 用于与乐观锁方案做性能对比 
 */
@Slf4j
@Service
public class RedisSeckillServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private SeckillOrderProducer seckillOrderProducer;

    /**
     * 预热秒杀库存到Redis
     */
    @Override
    public void prepareSeckill(Long voucherId, Integer stock) {
        String stockKey = SECKILL_STOCK_KEY + voucherId;
        stringRedisTemplate.opsForValue().set(stockKey, stock.toString());
        log.info("秒杀优惠券{}预热完成，库存：{}", voucherId, stock);
    }

    // Lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // Redis Key前缀
    private static final String SECKILL_STOCK_KEY = "seckill:stock:";
    private static final String SECKILL_ORDER_KEY = "seckill:orders:";
    /**
     * Redis+Lua秒杀实现
     */
    @Override
    public Result seckillVoucherRedisLua(Long voucherId) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        log.info("user imformation {}", userId);
        // 2. 生成订单ID
        Long orderId = redisIdWorker.nextId("order");
        log.info("orderid {}",orderId);
        // 3. 执行Lua脚本
        String stockKey = SECKILL_STOCK_KEY + voucherId;
        String orderKey = SECKILL_ORDER_KEY + voucherId;

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                java.util.Arrays.asList(stockKey, orderKey),
                userId.toString(),
                orderId.toString()
        );
        // 4. 判断结果
        int r = result.intValue();
        if (r == 0) {
            // 库存不足
            return Result.fail("stock not enough");
        }
        if (r == 2) {
            // 重复购买
            return Result.fail("repeat buy not allowed");
        }
        if (r == 3) {
            // 库存未初始化
            return Result.fail("seckill not started");
        }
        // 5. 秒杀成功，发送消息到队列异步创建订单
        Long stock = Long.parseLong(stringRedisTemplate.opsForValue().get(stockKey));
        log.info("stock {}", stock);
        SeckillOrderMessage message = new SeckillOrderMessage();
        message.setOrderId(orderId);
        message.setUserId(userId);
        message.setVoucherId(voucherId);
        message.setCreateTime(LocalDateTime.now());
        // 6. 发送消息到队列，使用JSON格式
        seckillOrderProducer.sendSeckillOrderMessage(
                RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                message
        );
        // 7. 返回成功结果
        log.info("user {} seckill voucher {} success,order id: {}", userId, voucherId, orderId);
        return Result.ok(orderId);
    }

    /**
     * 查询剩余库存
     */
    public Integer getStock(Long voucherId) {
        String stockKey = SECKILL_STOCK_KEY + voucherId;
        String stock = stringRedisTemplate.opsForValue().get(stockKey);
        return stock == null ? 0 : Integer.parseInt(stock);
    }
}
