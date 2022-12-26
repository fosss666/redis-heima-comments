package com.hmdp.config;

import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author fosss
 * @date 2022/12/26
 * redis配置类
 */
@Configuration
public class RedisConfig {
    @Value("${spring.redis.host}")
    private String host;
    @Value("${spring.redis.port}")
    private String port;
    @Value("${spring.redis.password}")
    private String password;

    //配置redisson
    @Bean
    public RedissonClient redissonClient() {
        String redisAddress = "redis://" + host + ":" + port;
        Config config = new Config();
        //config.useSingleServer().setAddress("redis://192.168.113.128:6379").setPassword("123456");
        config.useSingleServer().setAddress(redisAddress).setPassword(password);
        return Redisson.create(config);
    }

}











