package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData{
    private LocalDateTime expireTime; // 维护的过期时间
    private Object data; // 逻辑过期方案解决热点key问题时，这个字段可以理解为shop对象
}
