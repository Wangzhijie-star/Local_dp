package com.hmdp.mq;
import com.hmdp.dto.SeckillOrderMessage;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.UUID;
import cn.hutool.json.JSONUtil;



/**
 * 秒杀订单消息生产者
 * 负责将订单信息发送到消息队列
 */
@Slf4j
@Component
public class SeckillOrderProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;
    /**
     * 发送秒杀订单消息到队列
     * @param orderMessage 订单消息对象
     */
    // 发送可靠消息，携带消息ID（可使用UUID生成）
    public void sendSeckillOrderMessage(String exchange, String routingKey, SeckillOrderMessage orderMessage) {
        String messageId = UUID.randomUUID().toString();
        CorrelationData correlationData = new CorrelationData(messageId);
        try {
            // 将对象转换为JSON字符串
            String jsonMessage = JSONUtil.toJsonStr(orderMessage);
            
            // 创建消息，设置contentType为application/json
            MessageProperties messageProperties = new MessageProperties();
            messageProperties.setContentType("application/json");
            Message message = new Message(jsonMessage.getBytes(), messageProperties);
            
            rabbitTemplate.convertAndSend(
                    exchange,
                    routingKey,
                    message,
                    correlationData
            );
            log.info("消息发送成功（待确认），消息ID：{}，消息内容：{}" ,messageId , jsonMessage);
        } catch (Exception e) {
            log.error("秒杀订单消息发送失败，订单号：{}，错误：{}", messageId, e.getMessage());
            // 这里可以添加补偿机制，比如将消息存入数据库，定时重试
            e.printStackTrace();
        }
    }
}
