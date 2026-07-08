package com.kazama.redis_cache_demo;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
public abstract class AbstractPostgresIntegrationTest {

    // Deliberately NOT a @Testcontainers-managed @Container field: each subclass here is a
    // @DataJpaTest, and Spring boots a separate ApplicationContext per subclass (identical
    // annotations notwithstanding). A JUnit5-managed static container's lifecycle is scoped to
    // whichever concrete test class first triggers it, so a second subclass would otherwise spin
    // up its own second Postgres container -- which raced into a "connection refused" failure in
    // practice. Managing the container as a plain singleton (single JVM-wide start, reaped by
    // Testcontainers' Ryuk on JVM exit, never explicitly stopped) guarantees every subclass's
    // context talks to the exact same already-running container.
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
