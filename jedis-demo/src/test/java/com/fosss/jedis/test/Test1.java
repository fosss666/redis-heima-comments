package com.fosss.jedis.test;

import com.fosss.jedis.factory.JedisPoolFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.Map;

public class Test1 {
    private Jedis jedis;

    @BeforeEach
    void setUp() {
//        jedis=new Jedis("192.168.40.128",6379);
//        this.jedis.auth("redis2002zmlmf");

        //使用连接池获取jedis
        jedis = JedisPoolFactory.getJedis();

        jedis.select(0);//选择0号库
    }

    @AfterEach
    void tearDown() {
        if (jedis != null) {
            jedis.close();
        }
    }

    @Test
    public void testSpring() {
        String result = jedis.set("name", "zhangsan");
        System.out.println("result = " + result);
        String name = jedis.get("name");
        System.out.println("name = " + name);
    }

    @Test
    public void testHash() {
        jedis.hset("user:1", "id", "1");
        jedis.hset("user:1", "name", "林");

        Map<String, String> stringStringMap = jedis.hgetAll("user:1");
        System.out.println("stringStringMap = " + stringStringMap);
    }
}





















