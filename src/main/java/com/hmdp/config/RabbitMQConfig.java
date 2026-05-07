package com.hmdp.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import lombok.extern.slf4j.Slf4j;


/**
 * RabbitMQ 配置类
 * 定义秒杀订单相关的队列、交换机和绑定关系
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    /**
     * 秒杀订单队列
     */
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";

    /**
     * 秒杀订单交换机
     */
    public static final String SECKILL_ORDER_EXCHANGE = "seckill.order.exchange";

    /**
     * 秒杀订单路由键
     */
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";

    /**
     * 秒杀订单死信队列
     */
    public static final String SECKILL_ORDER_DLQ = "seckill.order.dlq";

    /**
     * 死信交换机（Direct类型，用于路由死信消息） 使用默认交换机
     */

    /**
     * 死信路由键（用于将死信消息路由到死信队列）
     */
    public static final String SECKILL_ORDER_DLQ_ROUTING_KEY = "seckill.order.dlq.routing.key";
    /**
     * 声明队列
     */
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_QUEUE)
                // 设置队列的最大长度，防止堆积过多消息
                .withArgument("x-max-length", 100000)
                // 设置消息过期时间为10分钟
                .withArgument("x-message-ttl", 600000)
                // 设置死信交换机（可选，用于处理失败消息）
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", SECKILL_ORDER_DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * 声明交换机
     */
    @Bean
    public DirectExchange seckillOrderExchange() {
        return new DirectExchange(SECKILL_ORDER_EXCHANGE);
    }
    /**
     * 声明死信队列（用于处理消费失败的消息）
     */
    @Bean
    public Queue seckillOrderDLQ() {
        return QueueBuilder.durable(SECKILL_ORDER_DLQ).build();
    }
    /**
     * 绑定队列到交换机
     */
    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder 
                .bind(seckillOrderQueue())
                .to(seckillOrderExchange())
                .with(SECKILL_ORDER_ROUTING_KEY);
    }
    //配置手动确认的异步确认回调
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        // 手动确认消息是否到达交换机
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            // correlationData：发送消息时携带的唯一标识（消息ID）
            // ack：true=消息到达交换机，false=未到达
            // cause：未到达时的失败原因
                if (ack) {
                    // 消息确认成功
                    log.info("seckill order message confirm success,order id: {}", correlationData);
                } else {
                    // 消息确认失败
                    log.error("seckill order message confirm failed,order id: {},error: {}", correlationData, cause);
                }
        });
        //消息返回回调，当消息未到达交换机时触发
        rabbitTemplate.setReturnCallback((returnedMessage, replyCode, replyText, exchange, routingKey) -> {
            // returnedMessage：包含消息、交换机、路由键、失败码等信息
            String messageContent = new String(returnedMessage.getBody());
            log.error("seckill order message routing failed: " +    
                    "exchange: " + exchange + 
                    ", routingKey: " + routingKey + 
                    ", messageContent: " + messageContent + 
                    ", replyCode: " + replyCode + 
                    ", replyText: " + replyText);
            // 路由失败处理（如重新路由、入库记录）
        });
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
        return rabbitTemplate;
    }
}
