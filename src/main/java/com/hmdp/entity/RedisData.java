package com.hmdp.entity;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class RedisData {
    //数据
    private Object data;
    //过期时间
    private LocalDateTime expireTime;
}
