package com.undertone.adselector.infrastructure.out;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.WatchServiceConfiguration;
import com.undertone.adselector.model.AdBudget;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static org.junit.jupiter.api.Assertions.*;

class FileBackedAdBudgetPlanStoreTest {

    @Test
    @SneakyThrows
    @DisplayName("Given a plan file with a single entry, when it is loaded, then it should contain only that entry")
    void loadPlanContent_givenPlanFileWithSingleEntry_thenStoreContainsSingleEntry_positive() {

        // GIVEN
        Path testPlanFile = createTestPlanFilePath(new AdBudgetMock("test1", 0.2, 100));
        FileBackedAdBudgetPlanStore sut = new FileBackedAdBudgetPlanStore(testPlanFile);

        // WHEN
        sut.loadPlanContent();

        // THEN
        Optional<AdBudget> actualAdBudgetOp = sut.fetch("test1");
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
        sut.loadPlanContent();

        // THEN

        // First entry
        Optional<AdBudget> actualFirstAdBudgetOp = sut.fetch("test1");
        assertTrue(actualFirstAdBudgetOp.isPresent());

        AdBudget actualFirstAdBudget = actualFirstAdBudgetOp.get();
        assertEquals("test1", actualFirstAdBudget.aid());
        assertEquals(0.2d, actualFirstAdBudget.priority());
        assertEquals(100, actualFirstAdBudget.quota());

        // Second entry
        Optional<AdBudget> actualSecondAdBudgetOp = sut.fetch("test2");
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
        Path testPlanFile = createTestPlanFilePath(
                new AdBudgetMock("test1", 0.2, 100),

                new InvalidAdBudgetMock(Map.of("aid", 12, "priority", 0.55d, "quota", 200)),

                new InvalidAdBudgetMock(Map.of("aid", "missingPriority", "quota", 200)),
                new InvalidAdBudgetMock(new HashMap<>(){{ put("aid", "nullPriority"); put("priority", null); put("quota", 200); }}),
                new InvalidAdBudgetMock(Map.of("aid", "StringPriority", "priority", "coco", "quota", 200)),

                new InvalidAdBudgetMock(Map.of("aid", "missingQuota", "priority", 0.55d)),
                new InvalidAdBudgetMock(new HashMap<>(){{ put("aid", "nullQuota"); put("priority", 0.55d); put("quota", null); }}),
                new InvalidAdBudgetMock(Map.of("aid", "StringQuota", "priority", 0.55d, "quota", "jumbo"))
        );

        FileBackedAdBudgetPlanStore sut = new FileBackedAdBudgetPlanStore(testPlanFile);

        // WHEN
        sut.loadPlanContent();

        // THEN

        // First entry
        Optional<AdBudget> actualFirstAdBudgetOp = sut.fetch("test1");
        assertTrue(actualFirstAdBudgetOp.isPresent());

        AdBudget actualFirstAdBudget = actualFirstAdBudgetOp.get();
        assertEquals("test1", actualFirstAdBudget.aid());
        assertEquals(0.2d, actualFirstAdBudget.priority());
        assertEquals(100, actualFirstAdBudget.quota());

        // Invalid entries
        assertTrue(sut.fetch("12").isEmpty());
        assertTrue(sut.fetch("missingPriority").isEmpty());
        assertTrue(sut.fetch("nullPriority").isEmpty());
        assertTrue(sut.fetch("StringPriority").isEmpty());
        assertTrue(sut.fetch("missingQuota").isEmpty());
        assertTrue(sut.fetch("nullQuota").isEmpty());
        assertTrue(sut.fetch("StringQuota").isEmpty());

    }

    @Test
    @DisplayName("Given a plan file with maximum of 10_000 entries, when it is loaded, then it should contain 10_000 matching entries")
    void loadPlanContent_givenPlanFileWithMaximumNumberOfEntries_thenStoreContainsAllEntries_positive() {

        // GIVEN
        AdBudgetMock[] arrayOfMaximumAdBudgetMocks = createArrayOfAdBudgetMocks(10_000);

        Path testPlanFile = createTestPlanFilePath(arrayOfMaximumAdBudgetMocks);

        FileBackedAdBudgetPlanStore sut = new FileBackedAdBudgetPlanStore(testPlanFile);

        // WHEN
        sut.loadPlanContent();

        // THEN
        for (AdBudgetMock adBudgetMock : arrayOfMaximumAdBudgetMocks) {
            Optional<AdBudget> actualAdBudgetOp = sut.fetch(adBudgetMock.aid());
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
        Executable loadPlanContent = () -> sut.loadPlanContent();

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

        FileBackedAdBudgetPlanStore sut =
                FileBackedAdBudgetPlanStore.builder(testPlanFile)
                        .withFileWatcher().build();

        // Test initial plan
        Optional<AdBudget> actualFirstAdBudgetOp = sut.fetch("test1");
        assertTrue(actualFirstAdBudgetOp.isPresent());

        AdBudget actualFirstAdBudget = actualFirstAdBudgetOp.get();
        assertEquals("test1", actualFirstAdBudget.aid());
        assertEquals(0.2d, actualFirstAdBudget.priority());
        assertEquals(100, actualFirstAdBudget.quota());

        // WHEN
        writeAdBudgetPlanToPath(testPlanFile, new AdBudgetMock("test2", 0.78d, 20));

        // THEN
        Assertions.assertTimeout(Duration.of(3, ChronoUnit.SECONDS), () -> {
            Optional<AdBudget> actualSecondAdBudgetOp;
            do {
                TimeUnit.MILLISECONDS.sleep(100);
                actualSecondAdBudgetOp = sut.fetch("test2");
            } while (actualSecondAdBudgetOp.isEmpty());

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

    @RequiredArgsConstructor
    class InvalidAdBudgetMock {

        final Map<String, Object> fields;

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
