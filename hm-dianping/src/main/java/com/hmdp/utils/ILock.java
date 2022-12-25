package com.hmdp.utils;

/**
 * @author fosss
 * @date 2022/12/25
 * 分布式锁
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 超时时间，超过后自动释放锁
     * @return 是否获取成功
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
