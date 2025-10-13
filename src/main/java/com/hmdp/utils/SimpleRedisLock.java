package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    // 锁名
    private static final String LOCK_NAME = "lock:";
    private static final String id_prefix = UUID.fastUUID().toString(true) + "-"; // 避免不同JVM产生的线程id不同

    // Redis对象
    private StringRedisTemplate stringRedisTemplate;
    private String name;

    // 加载lua脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 使用Redis分布式锁 key:锁名（统一前缀）+业务名+用户id value:线程id ex: timeoutSec
        // 1. 获取线程id，表明当前是哪个线程获取到了这把锁
        String id = id_prefix + Thread.currentThread().getId();
        // 2. 使用redis获取这把锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_NAME + name, id, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success); // 当success拆箱为null时，返回false
    }

    @Override
    public void unlock() {
//        String id = id_prefix + Thread.currentThread().getId();
//        String cacheId = stringRedisTemplate.opsForValue().get(LOCK_NAME + name);
//        if (cacheId.equals(id)) { // 使用当线程id标识相同时，才执行删除锁的操作
//            stringRedisTemplate.delete(LOCK_NAME + name);

        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(LOCK_NAME + name),
                id_prefix + Thread.currentThread().getId());
    }
}
