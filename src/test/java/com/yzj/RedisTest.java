package com.yzj;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;


@SpringBootTest
public class RedisTest {


    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Test
    void testString(){


        stringRedisTemplate.opsForValue().set("name","jack");

        Object name = stringRedisTemplate.opsForValue().get("name");

        System.out.println("name=" + name);
    }

//    void userTest(){
//
//    }
}
