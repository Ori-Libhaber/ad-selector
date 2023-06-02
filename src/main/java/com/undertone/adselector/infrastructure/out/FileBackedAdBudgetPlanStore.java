package com.undertone.adselector.infrastructure.out;

import com.jsoniter.JsonIterator;
import com.jsoniter.ValueType;
import com.jsoniter.any.Any;
import com.undertone.adselector.application.ports.in.UseCaseException;
import com.undertone.adselector.application.ports.out.InfrastructureException.StoreException.InitializationException;
import com.undertone.adselector.model.AdBudget;
import com.undertone.adselector.model.AdBudgetPlan;
import io.vavr.Lazy;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.String.format;
import static java.nio.file.Files.notExists;
import static java.nio.file.StandardWatchEventKinds.*;
import static java.util.Collections.emptyMap;
import static java.util.Objects.*;

@Slf4j
public final class FileBackedAdBudgetPlanStore implements AdBudgetPlan {

    private final Path planFile;
    private volatile Map<String, AdBudget> aidToAdBudgetMapping;

    FileBackedAdBudgetPlanStore(Path planFile) {
        this.planFile = requireNonNull(planFile, "Argument planFile must not be null");
        this.aidToAdBudgetMapping = emptyMap();
    }

    public static Builder builder(Path planFile) {
        return new Builder(planFile);
    }

    public static class Builder {

        private boolean withFileWatcher;
        private ExecutorService fileWatcherExecutor;

        private final Path planFile;

        Builder(Path planFile) {
            this.planFile = requireNonNull(planFile, "Argument planFile must not be null");
        }

        public Builder withFileWatcher() {
            this.withFileWatcher = true;
            return this;
        }

        public Builder usingFileWatcherExecutor(ExecutorService executor) {
            this.fileWatcherExecutor = requireNonNull(executor, "Argument executor must not be null");
            return this;
        }

        public FileBackedAdBudgetPlanStore build() throws InitializationException {

            if (notExists(planFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new InitializationException(format("File under path: %s does not exist", planFile));
            }

            var built = new FileBackedAdBudgetPlanStore(planFile).loadPlanContent();

            if (withFileWatcher) {
                activatePlanFileWatcher(built);
            }

            return built;
        }

        private ExecutorService defaultFileWatcherExecutor(){
            return Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName("plan-file-watcher");
                thread.setDaemon(true);
                return thread;
            });
        }

        private void activatePlanFileWatcher(FileBackedAdBudgetPlanStore built) throws InitializationException {

            var planFile = built.planFile;

            var watcherExecutor =
                    Optional.ofNullable(fileWatcherExecutor)
                            .orElseGet(this::defaultFileWatcherExecutor);

            WatchService watchService;
            try {

                planFile.getParent().register(
                        (watchService = planFile.getFileSystem().newWatchService()),
                        ENTRY_MODIFY
                );

                watcherExecutor.submit(() -> {
                    WatchKey key;
                    try {
                        while ((key = watchService.take()) != null) {
                            log.info("Detected changes to ad budget plan folder");
                            for (WatchEvent<?> event : key.pollEvents()) {
                                if (isPlanFileModified(event, planFile)) {
                                    built.loadPlanContent();
                                }
                            }
                            key.reset();
                        }
                    } catch (Exception ex) {
                        log.error("Exception raised while attempting to watch ad budget plan file: {}", planFile, ex);
                    }
                });

            } catch (IOException ioe) {
                throw new InitializationException(format("Failed to register watcher for ad budget plan file: %s", planFile), ioe);
            }
        }

    }

    private static boolean isPlanFileModified(WatchEvent<?> event, Path planFile) {
        if (ENTRY_MODIFY.equals(event.kind()) && (event.context() instanceof Path p)) {
            return planFile.getFileName().equals(p.getFileName());
        }
        return false;
    }

    private static AdBudget buildLazyAdBudget(String aid, Any priority, Any quota) {
        return Lazy.val(() -> Try.of(() -> AdBudgetImpl.build(aid, tryExtractValidPriority(priority), tryExtractValidQuota(quota)))
                .onFailure(ex -> log.error("Failed to create AdBudget from lazy instance, replacing with EMPTY", ex))
                    .getOrElse(AdBudget.EMPTY), AdBudget.class);
    }

    private static long tryExtractValidQuota(Any quotaAny) {
        if(Objects.equals(ValueType.NUMBER, quotaAny.valueType())){
            return quotaAny.toLong();
        }
        throw new UseCaseException.AbortedException.TypeConversionException(format("Field priority must be of type Number, but it was: %s", quotaAny));
    }

    private static double tryExtractValidPriority(Any priorityAny) throws UseCaseException.AbortedException.TypeConversionException {;
        if(Objects.equals(ValueType.NUMBER, priorityAny.valueType())){
            return priorityAny.toDouble();
        }
        throw new UseCaseException.AbortedException.TypeConversionException(format("Field priority must be of type Number, but it was: %s", priorityAny));
    }

    FileBackedAdBudgetPlanStore loadPlanContent() {
        log.info("Attempting to load ad budget plan from location: {}", planFile);

        final Instant start = Instant.now();
        try {
            Map<String, AdBudget> aidToAdBudget = new HashMap<>(10_110, 99f);
            Any adBudgetPlanJson = JsonIterator.parse(Files.newInputStream(planFile).readAllBytes()).readAny();

            /**
             * To improve performance, populating map with lazy instances of AdBudget.
             * The latter are created without field level type validation.
             * Any runtime deserialization issues will be logged and faulty entries would
             * be substituted by instances of AdBudget.EMPTY
             */
            for (Any adBudgetJson : adBudgetPlanJson.get("Ads").mustBeValid()) {
                try {
                    String aid = tryExtractValidAid(adBudgetJson);

                        aidToAdBudget.put(aid,
                                buildLazyAdBudget(aid,
                                    adBudgetJson.get("priority").mustBeValid(),
                                        adBudgetJson.get("quota").mustBeValid()));

                } catch (Exception ex) {
                    log.warn("Failed parsing entry: {} into AdBudget, skipping.", adBudgetJson);
                }
            }

            aidToAdBudgetMapping = aidToAdBudget;
            log.info("Finished loading ad budget plan in {} ms", Duration.between(start, Instant.now()).toMillis());
        } catch (Exception ex) {
            aidToAdBudgetMapping = emptyMap();
            log.error("Failed to load ad budget plan from location: {}", planFile, ex);
        }

        return this;
    }

    private String tryExtractValidAid(Any adBudgetJson) throws UseCaseException.AbortedException.TypeConversionException {
        Any aidAny = adBudgetJson.get("aid").mustBeValid();
        if(Objects.equals(ValueType.STRING, aidAny.valueType())){
            return aidAny.toString();
        }
        throw new UseCaseException.AbortedException.TypeConversionException(format("Field aid must be of type String, but it was: %s", aidAny));
    }

    @Override
    public Optional<AdBudget> fetch(String aid) {
        requireNonNull(aid, "Argument aid must not be null");

        if (aidToAdBudgetMapping.isEmpty()) {
            log.warn("Attempting to fetch AdBudget from empty AdBudgetPlan");
        }

        return Try.of(() -> aidToAdBudgetMapping.get(aid))
                .filterTry(Objects::nonNull)
                    .filterTry(lazyBudget -> !lazyBudget.isEmpty()) // triggers evaluation of lazy AdBudget
                        .toJavaOptional();
    }

    private record AdBudgetImpl(String aid, double priority, long quota) implements AdBudget {
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

        public static AdBudget build(String aid, double priority, long quota) {
            return new AdBudgetImpl(aid, priority, quota);
        }

    }
}
