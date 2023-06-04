package com.undertone.adselector.infrastructure.out;

import com.jsoniter.JsonIterator;
import com.jsoniter.ValueType;
import com.jsoniter.any.Any;
import com.undertone.adselector.application.ports.in.UseCaseException;
import com.undertone.adselector.application.ports.in.UseCaseException.AbortedException;
import com.undertone.adselector.application.ports.in.UseCaseException.AbortedException.TypeConversionException;
import com.undertone.adselector.application.ports.out.AdBudgetPlanStore;
import com.undertone.adselector.application.ports.out.InfrastructureException.StoreException;
import com.undertone.adselector.application.ports.out.InfrastructureException.StoreException.InitializationException;
import com.undertone.adselector.model.AdBudget;
import com.undertone.adselector.model.AdBudgetPlan;
import io.vavr.Lazy;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static io.vavr.control.Try.failure;
import static io.vavr.control.Try.success;
import static java.lang.String.format;
import static java.nio.file.Files.notExists;
import static java.nio.file.StandardWatchEventKinds.*;
import static java.util.Objects.*;
import static java.util.function.Predicate.not;

@Slf4j
public final class FileBackedAdBudgetPlanStore implements AdBudgetPlanStore {

    private final Path planFile;
    private volatile AdBudgetPlan adBudgetPlan;

    FileBackedAdBudgetPlanStore(Path planFile) {
        this.planFile = requireNonNull(planFile, "Argument planFile must not be null");
        this.adBudgetPlan = AdBudgetPlan.EMPTY;
    }

    @Override
    public Mono<AdBudgetPlan> fetchPlan() throws StoreException {
        return Mono.just(adBudgetPlan);
    }

    public static Builder builder(Path planFile) {
        return new Builder(planFile);
    }

    public static class Builder {

        private boolean withFileWatcher;
        private ExecutorService fileWatcherExecutor;

        private Consumer<Boolean> loadCompletionCallback;
        private final Path planFile;

        Builder(Path planFile) {
            this.planFile = requireNonNull(planFile, "Argument planFile must not be null");
            this.loadCompletionCallback = ignored -> {};
        }

        public Builder withFileWatcher() {
            this.withFileWatcher = true;
            return this;
        }

        /**
         * Enable plan file watcher service for automatic re-loading of plan file contents if those change on disk.
         * @param loadCompletionCallback provide some side effect free callback to execute once loading is complete.
         *                              Consumer input value true indicates success, false otherwise.
         *                              Please take extra care to avoid any lengthy calculations, callback will execute
         *                              on common thread pool.
         * @return Builder
         */
        public Builder withFileWatcher(Consumer<Boolean> loadCompletionCallback) {
            this.loadCompletionCallback = (status) -> {
                try {
                    loadCompletionCallback.accept(status);
                } catch (Exception ex) {
                    log.warn("Load completion callback raised exception", ex);
                }
            };
            return withFileWatcher();
        }

        public Builder usingFileWatcherExecutor(ExecutorService executor) {
            this.fileWatcherExecutor = requireNonNull(executor, "Argument executor must not be null");
            return this;
        }

        public FileBackedAdBudgetPlanStore build() throws InitializationException {

            var built = new FileBackedAdBudgetPlanStore(planFile).loadAdBudgetPlan();

            if (withFileWatcher) {
                activatePlanFileWatcher(built);
            }

            return built;
        }

        private ExecutorService defaultFileWatcherExecutor(){
            return Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName("plan-file-watcher");
                thread.setDaemon(true);
                return thread;
            });
        }

        private void activatePlanFileWatcher(FileBackedAdBudgetPlanStore built) throws InitializationException {

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

                            var executionGuard =
                                    Lazy.of(() -> nonNull(built.loadAdBudgetPlan()))
                                            .toCompletableFuture().thenAcceptAsync
                                                    (loadCompletionCallback, watcherExecutor);

                            for (WatchEvent<?> event : key.pollEvents()) {
                                if (isPlanFileModified(event)) {
                                    executionGuard.get();
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

        private boolean isPlanFileModified(WatchEvent<?> event) {
            if (ENTRY_MODIFY.equals(event.kind()) && (event.context() instanceof Path p)) {
                return planFile.getFileName().equals(p.getFileName());
            }
            return false;
        }

    }

    FileBackedAdBudgetPlanStore loadAdBudgetPlan() {
        log.info("Attempting to load ad budget plan from location: {}", planFile);

        final Instant start = Instant.now();

        AdBudgetPlan builtPlan = AdBudgetPlan.EMPTY;
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

            builtPlan = new InMemoryAdBudgetPlan(aidToAdBudget);

            log.info("Finished loading ad budget plan in {} ms", Duration.between(start, Instant.now()).toMillis());
        }
        catch (Exception ex) {
            log.error("Failed to load ad budget plan from location: {}", planFile, ex);
        }

        this.adBudgetPlan = builtPlan;

        return this;
    }

    private AdBudget buildLazyAdBudget(String aid, Any priority, Any quota) {
        return Lazy.val(() -> Try.of(() -> AdBudgetImpl.build(aid, tryExtractValidPriority(priority), tryExtractValidQuota(quota)))
                .onFailure(ex -> log.error("Failed to create AdBudget from lazy instance, replacing with EMPTY", ex))
                    .getOrElse(AdBudget.EMPTY), AdBudget.class);
    }

    private long tryExtractValidQuota(Any quotaAny) {
        if(Objects.equals(ValueType.NUMBER, quotaAny.valueType())){
            return quotaAny.toLong();
        }
        throw new TypeConversionException(format("Field priority must be of type Number, but it was: %s", quotaAny));
    }

    private double tryExtractValidPriority(Any priorityAny) throws TypeConversionException {
        if(Objects.equals(ValueType.NUMBER, priorityAny.valueType())){
            return priorityAny.toDouble();
        }
        throw new TypeConversionException(format("Field priority must be of type Number, but it was: %s", priorityAny));
    }

    private String tryExtractValidAid(Any adBudgetJson) throws TypeConversionException {
        Any aidAny = adBudgetJson.get("aid").mustBeValid();
        if(Objects.equals(ValueType.STRING, aidAny.valueType())){
            return aidAny.toString();
        }
        throw new TypeConversionException(format("Field aid must be of type String, but it was: %s", aidAny));
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

    static class InMemoryAdBudgetPlan implements AdBudgetPlan {

        private ConcurrentMap<String, AdBudget> aidToAdBudgetMapping;

        InMemoryAdBudgetPlan(Map<String, AdBudget> aidToAdBudgetMapping) {
            this.aidToAdBudgetMapping =
                    new ConcurrentHashMap<>(requireNonNull(aidToAdBudgetMapping,
                            "Argument aidToAdBudgetMapping must not be null"));
        }

        @Override
        public Optional<AdBudget> fetch(String aid) {
            requireNonNull(aid, "Argument aid must not be null");

            if (!aidToAdBudgetMapping.isEmpty()) {
                return Try.of(() -> aidToAdBudgetMapping.get(aid))
                        .filterTry(Objects::nonNull)
                            .peek(lazyBudget -> {
                                if (lazyBudget.isEmpty()) { // triggers evaluation of lazy AdBudget
                                    log.warn(new StringBuilder()
                                            .append("Fetching aid: \"").append(aid).append("\" produced an empty AdBudget.")
                                                .append(" Entry will be removed from internal mapping to reduce future processing times")
                                                    .toString());

                                    aidToAdBudgetMapping.remove(aid);
                                }
                            })
                            .filter(not(AdBudget::isEmpty))
                                .toJavaOptional();
            }

            log.warn("Attempting to fetch AdBudget from empty AdBudgetPlan");
            return Optional.empty();
        }

        @Override
        public boolean isEmpty() {
            return this.aidToAdBudgetMapping.isEmpty();
        }

    }

}
