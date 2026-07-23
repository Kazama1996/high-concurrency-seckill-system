package com.kazama.redis_cache_demo;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
public abstract class AbstractIntegrationTest {

//     Deliberately NOT @Testcontainers-managed @Container fields: the JUnit5 extension's
//     start/stop lifecycle for a @Container field is scoped to whichever concrete subclass
//     first triggers it, not shared across sibling subclasses -- see the identical reasoning
//     (and the "connection refused" race it caused in practice) in
//     AbstractPostgresIntegrationTest. This class got away with @Container so far only because
//     @SpringBootTest's slower startup happens to space out each subclass's trigger far enough
//     apart to avoid the race -- that spacing is incidental, not a guarantee. Managing all three
//     containers as plain singletons (single JVM-wide start, reaped by Testcontainers' Ryuk on
//     JVM exit, never explicitly stopped) removes the race regardless of subclass timing.
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine");

    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    private static final String REDIS_TEST_PASSWORD = "testpassword";

    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", REDIS_TEST_PASSWORD);

    static {
        postgres.start();
        kafka.start();
        redis.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        registry.add("common.redis.address",
                () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        registry.add("common.redis.password", () -> REDIS_TEST_PASSWORD);
    }
}
