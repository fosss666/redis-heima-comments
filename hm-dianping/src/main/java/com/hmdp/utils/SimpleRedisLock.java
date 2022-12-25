package com.hmdp.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.security.Key;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author fosss
 * @date 2022/12/25
 * 分布式锁
 */
public class SimpleRedisLock implements ILock {

    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private String name;
    private static final String LOCK_PREFIX = UUID.randomUUID().toString().replace("-", "");
    //lua脚本
    private static final DefaultRedisScript<Long> REDIS_SCRIPT;

    static {
        //用静态代码块对redis_script进行初始化
        REDIS_SCRIPT = new DefaultRedisScript<>();
        REDIS_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        REDIS_SCRIPT.setResultType(Long.class);
    }

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
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, LOCK_PREFIX + "-" + threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);//防止拆箱时出现空指针问题（flag为空时导致的拆箱空指针）
    }

    /**
     * 释放锁
     */

    //使用lua脚本保证多条命令的原子性
    @Override
    public void unlock() {
        long threadId = Thread.currentThread().getId();
        //调用lua脚本
        stringRedisTemplate.execute(REDIS_SCRIPT, Collections.singletonList(KEY_PREFIX + name), LOCK_PREFIX + threadId);
    }

/*
    @Override
    public void unlock() {
        //先判断是不是当前线程的锁，防止误删
        String lockValue = LOCK_PREFIX + Thread.currentThread().getId();
        String currentLockValue = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (lockValue.equals(currentLockValue)) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
    */
}















