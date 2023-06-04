package com.undertone.adselector.infrastructure.out;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.WatchServiceConfiguration;
import com.undertone.adselector.model.AdBudget;
import com.undertone.adselector.model.AdBudgetPlan;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mockito;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.spy;

@Slf4j
class FileBackedAdBudgetPlanStoreTest {

    @Test
    @SneakyThrows
    @DisplayName("Given a plan file with a single entry, when it is loaded, then it should contain only that entry")
    void loadPlanContent_givenPlanFileWithSingleEntry_thenStoreContainsSingleEntry_positive() {

        // GIVEN
        Path testPlanFile = createTestPlanFilePath(new AdBudgetMock("test1", 0.2, 100));
        FileBackedAdBudgetPlanStore sut = new FileBackedAdBudgetPlanStore(testPlanFile);

        // WHEN
        sut.loadAdBudgetPlan();

        // THEN
        AdBudgetPlan adBudgetPlan = sut.fetchPlan().block();
        Optional<AdBudget> actualAdBudgetOp = adBudgetPlan.fetch("test1");
        assertTrue(actualAdBudgetOp.isPresent());

        AdBudget actualAdBudget = actualAdBudgetOp.get();
        assertEquals("test1", actualAdBudget.aid());
        assertEquals(0.2d, actualAdBudget.priority());
        assertEquals(100, actualAdBudget.quota());

    }

    @Test
    @DisplayName("Given a plan file with two entries, when it is loaded, then it should contain only those two entries")
    void loadPlanContent_givenPlanFileWithTwoEntries_thenStoreContainsTwoEntries_positive() {

        // GIVEN
        Path testPlanFile = createTestPlanFilePath(
                new AdBudgetMock("test1", 0.2, 100),
                new AdBudgetMock("test2", 0.78, 20)
        );

        FileBackedAdBudgetPlanStore sut = new FileBackedAdBudgetPlanStore(testPlanFile);

        // WHEN
        sut.loadAdBudgetPlan();

        // THEN
        AdBudgetPlan adBudgetPlan = sut.fetchPlan().block();

        // First entry
        Optional<AdBudget> actualFirstAdBudgetOp = adBudgetPlan.fetch("test1");
        assertTrue(actualFirstAdBudgetOp.isPresent());

        AdBudget actualFirstAdBudget = actualFirstAdBudgetOp.get();
        assertEquals("test1", actualFirstAdBudget.aid());
        assertEquals(0.2d, actualFirstAdBudget.priority());
        assertEquals(100, actualFirstAdBudget.quota());

        // Second entry
        Optional<AdBudget> actualSecondAdBudgetOp = adBudgetPlan.fetch("test2");
        assertTrue(actualSecondAdBudgetOp.isPresent());

        AdBudget actualSecondAdBudget = actualSecondAdBudgetOp.get();
        assertEquals("test2", actualSecondAdBudget.aid());
        assertEquals(0.78d, actualSecondAdBudget.priority());
        assertEquals(20, actualSecondAdBudget.quota());

    }

    @Test
    @DisplayName("Given plan file contains invalid entry, when it is fetched, then it should not return")
    void loadPlanContent_givenPlanFileContainingInvalidEntry_thenStoreAllowsToFetchOnlyValidEntries_negative() {

        // GIVEN
        final AdBudgetMock validAdBudgetMock = new AdBudgetMock("test1", 0.2, 100);

        Path testPlanFile = createTestPlanFilePath(
                validAdBudgetMock,

                InvalidAdBudgetMock.from(validAdBudgetMock).aid(), // missingAid
                InvalidAdBudgetMock.from(validAdBudgetMock).aid(null), // nullAid
                InvalidAdBudgetMock.from(validAdBudgetMock).aid(12), // numberAid

                InvalidAdBudgetMock.from(validAdBudgetMock).aid("missingPriority").priority(),
                InvalidAdBudgetMock.from(validAdBudgetMock).aid("nullPriority").priority(null),
                InvalidAdBudgetMock.from(validAdBudgetMock).aid("StringPriority").priority("coco"),

                InvalidAdBudgetMock.from(validAdBudgetMock).aid("missingQuota").quota(),
                InvalidAdBudgetMock.from(validAdBudgetMock).aid("nullQuota").quota(null),
                InvalidAdBudgetMock.from(validAdBudgetMock).aid("StringQuota").priority("jumbo")
        );

        FileBackedAdBudgetPlanStore sut = new FileBackedAdBudgetPlanStore(testPlanFile);

        // WHEN
        sut.loadAdBudgetPlan();

        // THEN

        // First entry
        AdBudgetPlan adBudgetPlan = sut.fetchPlan().block();

        Optional<AdBudget> actualFirstAdBudgetOp = adBudgetPlan.fetch("test1");
        assertTrue(actualFirstAdBudgetOp.isPresent());

        AdBudget actualFirstAdBudget = actualFirstAdBudgetOp.get();
        assertEquals("test1", actualFirstAdBudget.aid());
        assertEquals(0.2d, actualFirstAdBudget.priority());
        assertEquals(100, actualFirstAdBudget.quota());

        // Invalid entries
        assertTrue(adBudgetPlan.fetch("null").isEmpty());
        assertTrue(adBudgetPlan.fetch("12").isEmpty());
        assertTrue(adBudgetPlan.fetch("missingPriority").isEmpty());
        assertTrue(adBudgetPlan.fetch("nullPriority").isEmpty());
        assertTrue(adBudgetPlan.fetch("StringPriority").isEmpty());
        assertTrue(adBudgetPlan.fetch("missingQuota").isEmpty());
        assertTrue(adBudgetPlan.fetch("nullQuota").isEmpty());
        assertTrue(adBudgetPlan.fetch("StringQuota").isEmpty());

    }

    @Test
    @DisplayName("Given a plan file with maximum of 10_000 entries, when it is loaded, then it should contain 10_000 matching entries")
    void loadPlanContent_givenPlanFileWithMaximumNumberOfEntries_thenStoreContainsAllEntries_positive() {

        // GIVEN
        AdBudgetMock[] arrayOfMaximumAdBudgetMocks = createArrayOfAdBudgetMocks(10_000);

        Path testPlanFile = createTestPlanFilePath(arrayOfMaximumAdBudgetMocks);

        FileBackedAdBudgetPlanStore sut = new FileBackedAdBudgetPlanStore(testPlanFile);

        // WHEN
        sut.loadAdBudgetPlan();

        // THEN
        for (AdBudgetMock adBudgetMock : arrayOfMaximumAdBudgetMocks) {
            AdBudgetPlan adBudgetPlan = sut.fetchPlan().block();
            Optional<AdBudget> actualAdBudgetOp = adBudgetPlan.fetch(adBudgetMock.aid());
            assertTrue(actualAdBudgetOp.isPresent());

            AdBudget actualAdBudget = actualAdBudgetOp.get();
            assertEquals(adBudgetMock.aid(), actualAdBudget.aid(), format("Expected: %s Actual: %s", adBudgetMock, actualAdBudget));
            assertEquals(adBudgetMock.priority(), actualAdBudget.priority(), format("Expected: %s Actual: %s", adBudgetMock, actualAdBudget));
            assertEquals(adBudgetMock.quota(), actualAdBudget.quota(), format("Expected: %s Actual: %s", adBudgetMock, actualAdBudget));
        }

    }

    @Test
    @DisplayName("Given path to non existing plan file, when path is loaded, then it should use empty plan")
    void loadPlanContent_givenPlanFileIsMissing_thenUseEmpty_negative() {

        // GIVEN
        Path nonExistingTestFile =
                Jimfs.newFileSystem(Configuration.unix())
                        .getPath("plan", "plan.json");

        FileBackedAdBudgetPlanStore sut = new FileBackedAdBudgetPlanStore(nonExistingTestFile);

        // WHEN
        Executable loadPlanContent = () -> sut.loadAdBudgetPlan();

        // THEN
        assertDoesNotThrow(loadPlanContent);

    }

    @Test
    @SneakyThrows
    @DisplayName("Given successfully built initial plan file, when plan file is changed, then it should load new plan")
    void build_givenBuilt_whenPlanFileChanged_thenLoadNewPlan_positive() {

        // GIVEN
        Path testPlanFile = createTestPlanFilePath(
                new AdBudgetMock("test1", 0.2, 100)
        );

        CountDownLatch planFileRefreshed = new CountDownLatch(1);

        FileBackedAdBudgetPlanStore sut =
                FileBackedAdBudgetPlanStore.builder(testPlanFile)
                        .withFileWatcher(status -> planFileRefreshed.countDown()).build();

        // Test initial plan
        AdBudgetPlan adBudgetPlan = sut.fetchPlan().block();
        Optional<AdBudget> actualFirstAdBudgetOp = adBudgetPlan.fetch("test1");
        assertTrue(actualFirstAdBudgetOp.isPresent());

        AdBudget actualFirstAdBudget = actualFirstAdBudgetOp.get();
        assertEquals("test1", actualFirstAdBudget.aid());
        assertEquals(0.2d, actualFirstAdBudget.priority());
        assertEquals(100, actualFirstAdBudget.quota());

        // WHEN
        writeAdBudgetPlanToPath(testPlanFile, new AdBudgetMock("test2", 0.78d, 20));

        // THEN
        Assertions.assertTimeout(Duration.of(3, ChronoUnit.SECONDS), () -> {
            planFileRefreshed.await(); // waiting for budget plan to finish loading
            AdBudgetPlan newAdBudgetPlan = sut.fetchPlan().block();
            Optional<AdBudget> actualSecondAdBudgetOp = newAdBudgetPlan.fetch("test2");

            // Second entry
            assertTrue(actualSecondAdBudgetOp.isPresent());

            AdBudget actualSecondAdBudget = actualSecondAdBudgetOp.get();
            assertEquals("test2", actualSecondAdBudget.aid());
            assertEquals(0.78d, actualSecondAdBudget.priority());
            assertEquals(20, actualSecondAdBudget.quota());
        });

    }

    @SneakyThrows
    Path createTestPlanFilePath(Object... adBudgetMocks) {

        // Creating mock file system
        FileSystem testFileSystem = Jimfs.newFileSystem
                (Configuration.unix().toBuilder().setWatchServiceConfiguration
                        (WatchServiceConfiguration.polling(10, TimeUnit.MILLISECONDS)).build());

        Path testPlanFile = testFileSystem.getPath("plan", "plan.json");

        // Creating file
        Files.createDirectories(testPlanFile.getParent());
        Files.createFile(testPlanFile);

        writeAdBudgetPlanToPath(testPlanFile, adBudgetMocks);

        return testPlanFile;
    }

    @SneakyThrows
    void writeAdBudgetPlanToPath(Path testPlanFile, Object... adBudgetMocks) {

        // Joining ad budget entries
        StringJoiner adBudgets = new StringJoiner(",").setEmptyValue("");
        for(Object adBudgetMock : adBudgetMocks) {
            adBudgets.add(adBudgetMock.toString());
        }

        // Writing final result to file
        Files.writeString(testPlanFile, format("""
                {
                  "Ads": [
                    %s
                  ]
                }
                """, adBudgets));

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

    @NoArgsConstructor
    static class InvalidAdBudgetMock {

        final Map<String, Object> fields = new HashMap<>();

        private InvalidAdBudgetMock(InvalidAdBudgetMock other) {
            this.fields.putAll(other.fields);
        }

        static InvalidAdBudgetMock from(AdBudgetMock sourceMock) {
            return new InvalidAdBudgetMock()
                    .aid(sourceMock.aid)
                        .priority(sourceMock.priority)
                            .quota(sourceMock.quota);
        }

        static InvalidAdBudgetMock from(InvalidAdBudgetMock sourceMock) {
            return new InvalidAdBudgetMock(sourceMock);
        }

        InvalidAdBudgetMock with(String key, Object value) {
            this.fields.put(key, value);
            return this;
        }

        InvalidAdBudgetMock withOut(String key) {
            this.fields.remove(key);
            return this;
        }

        InvalidAdBudgetMock aid(){
            return withOut("aid");
        }

        InvalidAdBudgetMock aid(Object value){
            return with("aid", value);
        }

        InvalidAdBudgetMock priority(Object value){
            return with("priority", value);
        }

        InvalidAdBudgetMock priority(){
            return withOut("priority");
        }

        InvalidAdBudgetMock quota(Object value){
            return with("quota", value);
        }

        InvalidAdBudgetMock quota(){
            return withOut("quota");
        }

        @Override
        public String toString() {
            StringJoiner joiner =
                    new StringJoiner(",", "{", "}")
                            .setEmptyValue("");

            for (String key : fields.keySet()) {
                Object value = fields.get(key);
                if (isNull(value)) {
                    joiner.add(format("\"%s\": null", key));
                } else if (value instanceof Float f) {
                    joiner.add(format("\"%s\": %.2f", key, f));
                } else if (value instanceof Double d) {
                    joiner.add(format("\"%s\": %.2f", key, d));
                } else if (value instanceof Integer i) {
                    joiner.add(format("\"%s\": %d", key, i));
                } else if (value instanceof Long l) {
                    joiner.add(format("\"%s\": %d", key, l));
                } else {
                    joiner.add(format("\"%s\": \"%s\"", key, value));
                }
            }
            return joiner.toString();
        }
    }

    private static AdBudgetMock[] createArrayOfAdBudgetMocks(int count) {
        var budgetMocks = new AdBudgetMock[count];
        Random r = new Random();
        for (int i = 0; i < budgetMocks.length; i++) {
            budgetMocks[i] = new AdBudgetMock("test" + i,
                    (int)(100 * r.nextDouble(0.01d, 0.99d))/100d,
                    r.nextLong(100, 4000));
        }

        return budgetMocks;
    }

}
