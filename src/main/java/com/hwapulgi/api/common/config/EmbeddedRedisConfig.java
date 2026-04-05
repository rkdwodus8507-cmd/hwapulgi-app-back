package com.hwapulgi.api.common.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

@Configuration
@Profile("local")
public class EmbeddedRedisConfig {

    @Value("${spring.data.redis.port}")
    private int redisPort;

    private RedisServer redisServer;

    @PostConstruct
    public void start() {
        redisServer = new RedisServer(redisPort);
        try {
            redisServer.start();
        } catch (Exception e) {
            // port already in use — skip
        }
    }

    @PreDestroy
    public void stop() {
        if (redisServer != null && redisServer.isActive()) {
            redisServer.stop();
        }
    }
}
