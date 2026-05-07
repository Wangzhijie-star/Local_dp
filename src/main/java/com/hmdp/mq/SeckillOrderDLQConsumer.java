package com.hmdp.mq;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.amqp.core.Message;

import com.hmdp.config.RabbitMQConfig;
import com.rabbitmq.client.Channel;

/**
 * 秒杀订单死信队列消费者
 * 负责处理死信队列中的消息
 * 记录日志，方便调试和监控
 * 发起重试请求，尝试处理失败的消息，促成订单处理
 */
@Slf4j
@Component
public class SeckillOrderDLQConsumer{
    @Autowired
    private RabbitTemplate rabbitTemplate;
    // 监听死信队列
    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_DLQ)
    public void consumeDlxMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String messageContent = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            // 1. 记录死信消息日志（便于排查）
            log.info("seckill order dlq message receive,message id: {},content: {}", message.getMessageProperties().getMessageId(), messageContent);
            // 2. 自动重试（设置重试次数，此处示例重试1次）
            Integer retryCount = message.getMessageProperties().getHeader("retryCount");
            if (retryCount == null || retryCount < 1) {
                // 第一次重试，设置重试次数，重新发送到业务队列
                message.getMessageProperties().setHeader("retryCount", 1);
                rabbitTemplate.send(RabbitMQConfig.SECKILL_ORDER_QUEUE, message);
                log.info("seckill order dlq message retry send,message id: {}", message.getMessageProperties().getMessageId());
            } else {
                // 重试次数用尽，记录日志，人工处理
                log.warn("seckill order dlq message retry count exhausted,message id: {},please handle manually", message.getMessageProperties().getMessageId());
            }
            // 3. ACK死信消息（无论是否重试，都需ACK，避免死信队列堆积）
            channel.basicAck(deliveryTag, false);
        } catch (Exception e){
            // 死信处理异常，直接ACK，避免死信队列阻塞
            channel.basicAck(deliveryTag, false);
            log.error("seckill order dlq message process failed,message id: {},error: {}", message.getMessageProperties().getMessageId(), e.getMessage());
        }
    }
}
