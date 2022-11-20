package com.fosss.springdataredis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fosss.springdataredis.pojo.User;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

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
        redisTemplate.opsForValue().set("user:99",new User("李四",99));
        User user = (User) redisTemplate.opsForValue().get("user:99");
        System.out.println("user = " + user);
    }


    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final ObjectMapper objectMapper=new ObjectMapper();

    @Test
    void testSpringRedisTemplate(){
        stringRedisTemplate.opsForValue().set("name","张三");
        String name = stringRedisTemplate.opsForValue().get("name");
        System.out.println("name = " + name);
    }

    @Test
    void testSpringRedisTemplate2() throws JsonProcessingException {
        User user = new User("李四", 22);
        String s = objectMapper.writeValueAsString(user);
        stringRedisTemplate.opsForValue().set("user:100",s );

        String s1 = stringRedisTemplate.opsForValue().get("user:100");
        System.out.println("s1 = " + s1);
        User user1 = objectMapper.readValue(s1, User.class);
        System.out.println("user1 = " + user1);
    }
}


















