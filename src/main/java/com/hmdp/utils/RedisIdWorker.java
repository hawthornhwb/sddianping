package com.hmdp.utils;


import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class RedisIdWorker {

    private static final long START_TIMESTAMP = 1735689600L; // 2025年1月1日的时间戳

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix, int offset) {
        // 高31位时间戳构造
        LocalDateTime now = LocalDateTime.now(); // 获取当前时间
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC); // 将当前时间转化为秒
        long timestamp = nowSecond - START_TIMESTAMP; // 以当前时间到2025年1月1日的差值作为时间戳

        // 低32位的序列号构造
        // 在redis中以天为单位记录key
        String dayFormat = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + dayFormat, timestamp); // 当key不存在时，redis会创建key，因而不会出现空指针异常


        return timestamp << offset | increment;
    }

//    public static void main(String[] args) {
//        LocalDateTime now = LocalDateTime.of(2025, 1, 1, 0, 0); // 获取2025年1月1日的LocalDateTime格式的时间
//        long nowSecond = now.toEpochSecond(ZoneOffset.UTC); // 将LocalDateTime格式的时间转化为秒
//        System.out.println(nowSecond); // 打印该时间
//    }
}
