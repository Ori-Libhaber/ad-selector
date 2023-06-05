package com.undertone.adselector.infrastructure.out;

import com.undertone.adselector.model.AdBudget;
import com.undertone.adselector.model.AdDistribution;
import com.undertone.adselector.model.Status;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class RedisBackedAdDistributionStoreTest {

    @Container
    public static GenericContainer<?> redis =
            new GenericContainer(DockerImageName.parse("redis/redis-stack:latest"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    private static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
    }

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @BeforeAll
    public static void beforeAll(){
        redis.withReuse(true);
        redis.start();
    }

    @AfterAll
    public static void afterAll(){
        redis.stop();
    }

    @BeforeEach
    public void beforeEach(){
        redisTemplate.opsForValue().set("testDist", String.valueOf(9)).block();
        redisTemplate.opsForValue().set("testExhaustedDist", String.valueOf(15)).block();
    }

    @Test
    @DisplayName("Given distributions exists, when fetching, then return AdDistributions containing Redis values accordingly")
    void fetchDistributions_givenDistributionExists_whenFetching_thenReturnAdDistributionInstance_positive() {

        // GIVEN
        var sut = new RedisBackedAdDistributionStore(redisTemplate);
        var adBudgets = List.<AdBudget>of(new AdBudgetMock("testDist", 0.04, 20));

        // WHEN
        Mono<List<AdDistribution>> actualMonoResponse = sut.fetchDistributions(adBudgets);

        // THEN
        List<AdDistribution> actualListOfDist = actualMonoResponse.block();
        assertNotNull(actualListOfDist, "Expected non empty Mono");

        assertEquals(1, actualListOfDist.size(), "Expected exactly one AdDistribution instance");

        AdDistribution actualAdDistribution = actualListOfDist.get(0);
        assertEquals("testDist", actualAdDistribution.aid(), "Expected aid to match");
        assertEquals(0.04, actualAdDistribution.priority(), "Expected priority to match");
        assertEquals(20, actualAdDistribution.quota(), "Expected quota to match");
        assertEquals(11, actualAdDistribution.remainingQuota(), "Expected remainingQuota to match");

    }

    @Test
    @DisplayName("Given missing distributions, when fetching, then instantiate values on Redis and return AdDistribution instances accordingly")
    void fetchDistributions_givenMissingDistributions_whenFetching_thenInitialiseAndReturnAdDistributionInstances_positive() {

        // GIVEN
        var sut = new RedisBackedAdDistributionStore(redisTemplate);
        var testRandomAid = "testDist_".concat(String.valueOf(Instant.now().toEpochMilli()));
        var adBudgets = List.<AdBudget>of(new AdBudgetMock(testRandomAid, 0.14, 30));

        // WHEN
        Mono<List<AdDistribution>> actualMonoResponse = sut.fetchDistributions(adBudgets);

        // THEN
        List<AdDistribution> actualListOfDist = actualMonoResponse.block();
        assertNotNull(actualListOfDist, "Expected non empty Mono");

        assertEquals(1, actualListOfDist.size(), "Expected exactly one AdDistribution instance");

        AdDistribution actualAdDistribution = actualListOfDist.get(0);
        assertEquals(testRandomAid, actualAdDistribution.aid(), "Expected aid to match");
        assertEquals(0.14, actualAdDistribution.priority(), "Expected priority to match");
        assertEquals(30, actualAdDistribution.quota(), "Expected quota to match");
        assertEquals(30, actualAdDistribution.remainingQuota(), "Expected remainingQuota to match");

    }

    @Test
    @DisplayName("Given distribution exists, when fetching, then return AdDistribution accordingly")
    void fetchDistribution_givenDistributionExists_whenFetching_thenReturnAdDistributionInstance_positive() {

        // GIVEN
        var sut = new RedisBackedAdDistributionStore(redisTemplate);
        var adBudget = new AdBudgetMock("testDist", 0.04, 20);

        // WHEN
        Mono<AdDistribution> actualMonoResponse = sut.fetchDistribution(adBudget);

        // THEN
        AdDistribution actualDist = actualMonoResponse.block();
        assertNotNull(actualDist, "Expected non empty Mono");

        assertEquals("testDist", actualDist.aid(), "Expected aid to match");
        assertEquals(0.04, actualDist.priority(), "Expected priority to match");
        assertEquals(20, actualDist.quota(), "Expected quota to match");
        assertEquals(11, actualDist.remainingQuota(), "Expected remainingQuota to match");

    }

    @Test
    @DisplayName("Given missing distribution, when fetching, then instantiate value on Redis and return AdDistribution instance accordingly")
    void fetchDistribution_givenMissingDistribution_whenFetching_thenInitialiseAndReturnAdDistributionInstance_positive() {

        // GIVEN
        var sut = new RedisBackedAdDistributionStore(redisTemplate);
        var testRandomAid = "testDist_".concat(String.valueOf(Instant.now().toEpochMilli()));
        var adBudget = new AdBudgetMock(testRandomAid, 0.44, 60);

        // WHEN
        Mono<AdDistribution> actualMonoResponse = sut.fetchDistribution(adBudget);

        // THEN
        AdDistribution actualDist = actualMonoResponse.block();
        assertNotNull(actualDist, "Expected non empty Mono");

        assertEquals(testRandomAid, actualDist.aid(), "Expected aid to match");
        assertEquals(0.44, actualDist.priority(), "Expected priority to match");
        assertEquals(60, actualDist.quota(), "Expected quota to match");
        assertEquals(60, actualDist.remainingQuota(), "Expected remainingQuota to match");

    }

    @Test
    @DisplayName("Given incrementing, when response value is within quota bounds, then return status SUCCESS")
    void incrementDistribution_givenIncrementing_whenResponseWithinQuotaBounds_thenReturnSuccess_positive() {

        // GIVEN
        var sut = new RedisBackedAdDistributionStore(redisTemplate);
        var adDistMock = new AdDistributionMock("testDist", 0.04, 10, 1);

        // WHEN
        Mono<Status> actualMonoResponse = sut.incrementDistribution(adDistMock);

        // THEN
        Status actualStatus = actualMonoResponse.block();
        assertNotNull(actualStatus, "Expected non empty Mono");

        assertEquals(Status.SUCCESS, actualStatus, "Expected SUCCESS status");

    }

    @Test
    @DisplayName("Given incrementing, when response value exceeds quota, then rollback and return status CONFLICT")
    void incrementDistribution_givenIncrementing_whenResponseExceedsQuotaBounds_thenReturnConflict_negative() {

        // GIVEN
        var sut = new RedisBackedAdDistributionStore(redisTemplate);
        var adDistMock = new AdDistributionMock("testDist", 0.04, 9, 1);

        // WHEN
        Mono<Status> actualMonoResponse = sut.incrementDistribution(adDistMock);

        // THEN
        Status actualStatus = actualMonoResponse.block();
        assertNotNull(actualStatus, "Expected non empty Mono");

        assertEquals(Status.CONFLICT, actualStatus);
        String actualRolledBackValue = redisTemplate.opsForValue().get("testDist").block();
        assertEquals(String.valueOf(9), actualRolledBackValue, "Expected value rollback");

    }

    @Test
    @DisplayName("Given incrementing, when exhausted quota, then return status FAILURE")
    void incrementDistribution_givenIncrementing_whenExhaustedQuota_thenReturnFailure_negative() {

        // GIVEN
        var sut = new RedisBackedAdDistributionStore(redisTemplate);
        var adDistMock = new AdDistributionMock("testDist", 0.04, 9, 0);

        // WHEN
        Mono<Status> actualMonoResponse = sut.incrementDistribution(adDistMock);

        // THEN
        Status actualStatus = actualMonoResponse.block();
        assertNotNull(actualStatus, "Expected non empty Mono");

        assertEquals(Status.FAILURE, actualStatus);

    }

    record AdBudgetMock(String aid, double priority, long quota) implements AdBudget {
        @Override
        public String toString() {
            return format(
                    """
                    {
                      "aid": "%s",
                      "priority": %.2f,
                      "quota": %d
                    }
                    """, aid, priority, quota);
        }
    }

    record AdDistributionMock(String aid, double priority, long quota, long remainingQuota) implements AdDistribution {
        @Override
        public String toString() {
            return format(
                    """
                    {
                      "aid": "%s",
                      "priority": %.2f,
                      "quota": %d,
                      "remaining": %d
                    }
                    """, aid, priority, quota, remainingQuota);
        }

        public AdBudget asAdBudget(){
            return this;
        }

    }

    @TestConfiguration
    public static class TestConfig {

    }

}
