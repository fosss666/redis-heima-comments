package com.fosss.springdataredis;

import com.fosss.springdataredis.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
class SpringDataRedisApplicationTests {

    @Autowired
//    private RedisTemplate redisTemplate;
    private RedisTemplate<String,Object> redisTemplate;

    @Test
    void testRedis() {
        redisTemplate.opsForValue().set("name","大名");
        Object name = redisTemplate.opsForValue().get("name");
        System.out.println("name = " + name);
    }

    @Test
    void testRedis2() {
        redisTemplate.opsForValue().set("user:100",new User("李四",99));
        User user = (User) redisTemplate.opsForValue().get("user:100");
        System.out.println("user = " + user);
    }

}


















