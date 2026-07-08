package com.kazama.redis_cache_demo;

import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Tag("integration")
public abstract class AbstractRedisIntegrationTest {

    protected static final String REDIS_TEST_PASSWORD = "testpassword";

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", REDIS_TEST_PASSWORD);

    protected static String redisAddress() {
        return "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379);
    }

    protected static String redisHost() {
        return redis.getHost();
    }

    protected static int redisPort() {
        return redis.getMappedPort(6379);
    }
}
