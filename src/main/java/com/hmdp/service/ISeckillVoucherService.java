package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
public interface ISeckillVoucherService extends IService<SeckillVoucher> {
    /**
     * Redis+Lua秒杀实现
     */
    Result seckillVoucherRedisLua(Long voucherId);
    /**
     * 预热秒杀库存到Redis
     */
    void prepareSeckill(Long voucherId, Integer stock);

}
