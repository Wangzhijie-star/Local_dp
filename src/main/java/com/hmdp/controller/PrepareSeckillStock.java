package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping("/seckill-test") 
public class PrepareSeckillStock { 
    /** 
     * 预热秒杀库存到Redis
     * 测试Redis方案前必须先调用此接口
     */
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @PostMapping("/prepare/{voucherId}")
    public Result prepareSeckill(@PathVariable Long voucherId) {
        // 从数据库获取库存
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }

        // 预热到Redis
        seckillVoucherService.prepareSeckill(voucherId, voucher.getStock());
        return Result.ok("预热完成");
    }
}
