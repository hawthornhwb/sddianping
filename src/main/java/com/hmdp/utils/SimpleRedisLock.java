package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    // 锁名
    private static final String LOCK_NAME = "lock:";

    // Redis对象
    private StringRedisTemplate stringRedisTemplate;
    private String name;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 使用Redis分布式锁 key:锁名（统一前缀）+业务名+用户id value:线程id ex: timeoutSec
        // 1. 获取线程id，表明当前是哪个线程获取到了这把锁
        long id = Thread.currentThread().getId();
        // 2. 使用redis获取这把锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_NAME + name, id + "", timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success); // 当success拆箱为null时，返回false
    }

    @Override
    public void unlock() {
        stringRedisTemplate.delete(LOCK_NAME + name);
    }
}
