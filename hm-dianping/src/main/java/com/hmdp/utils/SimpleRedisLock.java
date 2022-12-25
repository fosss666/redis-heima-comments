package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author fosss
 * @date 2022/12/25
 * 分布式锁
 */
public class SimpleRedisLock implements ILock {

    private StringRedisTemplate stringRedisTemplate;
    private static final String keyPrefix = "lock:";
    private String name;
    private static final String lockPrefix = UUID.randomUUID().toString().replace("-", "");

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    /**
     * 尝试获取锁
     *
     * @param timeoutSec 超时时间，超过后自动释放锁
     * @return 是否获取成功
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        //获取当前线程id作为value
        long threadId = Thread.currentThread().getId();
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(keyPrefix + name, lockPrefix + "-" + threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);//防止拆箱时出现空指针问题（flag为空时导致的拆箱空指针）
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        //先判断是不是当前线程的锁，防止误删
        String lockValue = lockPrefix + Thread.currentThread().getId();
        String currentLockValue = stringRedisTemplate.opsForValue().get(keyPrefix + name);
        if (lockValue.equals(currentLockValue)) {
            stringRedisTemplate.delete(keyPrefix + name);
        }
    }
}















