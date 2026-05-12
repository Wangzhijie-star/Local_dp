package com.hmdp.mq;

import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.rabbitmq.client.Channel;

import cn.hutool.json.JSONUtil;

/**
 * 秒杀订单消息消费者
 * 异步处理订单创建逻辑
 */
@Slf4j
@Component
public class SeckillOrderConsumer {

    @Autowired
    private IVoucherOrderService voucherOrderService;

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    /**
     * 监听秒杀订单队列，异步创建订单
     *
     * @param message 订单消息
     */
    @RabbitListener(queues = "seckill.order.queue")
    @Transactional
    public void handleSeckillOrder(Message message, Channel channel) throws Exception {
        // 消息标识
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        
        // 解析消息体
        String messageBody = new String(message.getBody());
        log.info("success receive seckill order message: {}", messageBody);
        
        SeckillOrderMessage orderMessage;
        try {
            orderMessage = JSONUtil.toBean(messageBody, SeckillOrderMessage.class);
        } catch (Exception e) {
            log.error("parse seckill order message failed: {}, error: {}", messageBody, e.getMessage());
            // 解析失败，拒绝消息，进入死信队列
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        
        log.info("parse seckill order message success: orderId={}, userId={}, voucherId={}", 
                orderMessage.getOrderId(), orderMessage.getUserId(), orderMessage.getVoucherId());

        // 1. 检查订单是否存在,如果存在则跳过处理，保证幂等性
        VoucherOrder existOrder = voucherOrderService.getById(orderMessage.getOrderId());
        if (existOrder != null) {
            log.info("seckill order already exists,skip processing: {}", orderMessage.getOrderId());
            // 手动确认消息，避免重复处理
            channel.basicAck(deliveryTag, false);
            return;
        }
        //查库存，乐观锁扣减
        SeckillVoucher voucher = seckillVoucherService.getById(orderMessage.getVoucherId());
        if (voucher == null || voucher.getStock() <= 0) {
            log.error("seckill voucher stock not enough,voucher id: {},order id: {}", orderMessage.getVoucherId(), orderMessage.getOrderId());
            channel.basicAck(deliveryTag,false);
            return;
        }

        try {
            // 1. 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setId(orderMessage.getOrderId());
            voucherOrder.setUserId(orderMessage.getUserId());
            voucherOrder.setVoucherId(orderMessage.getVoucherId());
            voucherOrder.setCreateTime(orderMessage.getCreateTime());
            voucherOrder.setUpdateTime(orderMessage.getCreateTime());

            // 2. 保存订单到数据库
            voucherOrderService.save(voucherOrder);

            // 3. 扣减数据库库存（最终一致性保障）
            // 注意：Redis已经扣减了库存，这里是为了保证数据库和Redis的一致性
            // 实际生产环境中，可以通过定时任务或库存对账来保障一致性
            seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", orderMessage.getVoucherId())
                    .gt("stock", 0)
                    .update();

            log.info("order create success,order id: {},user id: {},voucher id: {}",
                    orderMessage.getOrderId(), orderMessage.getUserId(), orderMessage.getVoucherId());
            // 手动确认消息，避免重复处理
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("order create failed,order id: {},error:  {}", orderMessage.getOrderId(), e.getMessage());
            // 手动拒绝消息，避免重复处理，我们在mq中配置死信队列，失败后会自动重试
            channel.basicNack(deliveryTag, false, false);
            return;
        }
    }
}
