package com.fosss.jedis.factory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisPoolFactory {
    private static final JedisPool jedisPool;



    static {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(8);
        jedisPoolConfig.setMaxIdle(8);
        jedisPoolConfig.setMinIdle(0);
        jedisPoolConfig.setMaxWaitMillis(1000);
        jedisPool=new JedisPool(jedisPoolConfig,"192.168.40.128",6379,1000,"redis2002zmlmf");
    }

    //获取jedis的静态方法
    public static Jedis getJedis(){
        return jedisPool.getResource();
    }
}
