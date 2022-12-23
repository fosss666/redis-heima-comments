package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author fosss
 * @date 2022/12/23
 * redis方法工具类
 */
@Component
public class RedisHelper {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 方法一
     * 将对象加入缓存
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    /**
     * 方法二
     * 将对象加入缓存，用逻辑过期时间
     */
    public void setWithLogicalExpire(String key, Object value, Long expireTime, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expireTime)));
        redisData.setData(value);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 方法三
     * 查询数据并防止缓存穿透
     *
     * @param id       对象的id
     * @param idPrefix id前缀
     * @param type     返回数据类型
     * @param function 函数
     * @param time     过期时间
     * @param timeUnit 过期时间单位
     * @return 查询到的数据
     */
    public <T, ID> T queryWithWalkThrough(ID id, String idPrefix, Class<T> type, Function<ID, T> function, Long time, TimeUnit timeUnit) {
        String key = idPrefix + id;
        //先从redis中查询
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断非空
        if (!StringUtils.isEmpty(json)) {
            //转成对象返回
            return JSONUtil.toBean(json, type);
        }

        if ("".equals(json)) {//防止缓存击穿
            //数据为空字符串，报错
            return null;
        }

        //缓存中没有数据，则从数据库中查询
        T data = function.apply(id);
        //判断非空
        if (data == null) {
            //向缓存中存储空字符串，目的是解决缓存穿透问题（缓存和数据库中都没有该数据）
            stringRedisTemplate.opsForValue().set(key, "", time, timeUnit);//防止缓存击穿
            return null;
        }
        //放入缓存中
        this.set(key, JSONUtil.toJsonStr(data), time, timeUnit);
        return data;
    }

    /**
     * 方法四
     * 用逻辑过期时间解决缓存击穿
     *
     * @param id
     * @return
     */
    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <T, ID> T queryWithExpire(ID id, String idPrefix, Class<T> type, Function<ID, T> function, Long time, TimeUnit timeUnit) {
        String key = idPrefix + id;
        //先从redis中查询
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否命中
        if (StringUtils.isEmpty(json)) {
            //没有命中
            return null;
        }

        //命中，判断缓存是否过期
        //获取缓存的数据和逻辑过期时间
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        Object data = redisData.getData();
        T bean = JSONUtil.toBean((JSONObject) data, type);

        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //没有过期，返回商铺信息
            return bean;
        }

        //过期，尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean flag = tryLock(lockKey);
        if (flag) {
            //获取成功，开启独立线程,实现缓存的重建
            CACHE_REBUILD_EXECUTOR.execute(() -> {
                try {
                    //从数据库查询数据
                    T t = function.apply(id);

                    //存入redis
                    setWithLogicalExpire(key, t, time, timeUnit);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //返回过期的商户信息
        return bean;
    }

    /**
     * 上锁
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);//做拆箱操作
    }

    /**
     * 解锁
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

}































