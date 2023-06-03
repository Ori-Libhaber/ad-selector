package com.undertone.adselector.application.adaptors.services;

import com.undertone.adselector.application.ports.in.AdSelectionStrategy;
import com.undertone.adselector.application.ports.in.SelectAdUseCase;
import com.undertone.adselector.application.ports.out.AdBudgetPlanStore;
import com.undertone.adselector.model.AdBudget;
import com.undertone.adselector.model.exceptions.ApplicationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.time.Duration.of;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static reactor.util.retry.Retry.fixedDelay;

@Component
public class AdSelectionService implements SelectAdUseCase {

    private final AdSelectionStrategy selectionStrategy;

    private final AdBudgetPlanStore planStore;

    @Autowired
    public AdSelectionService(AdSelectionStrategy selectionStrategy, AdBudgetPlanStore planStore) {
        this.selectionStrategy = requireNonNull(selectionStrategy, "Argument selectionStrategy must not be null");
        this.planStore = requireNonNull(planStore, "Argument planStore must not be null");
    }

    @Override
    public Mono<Optional<String>> selectAd(Set<String> population) throws ApplicationException {
        return planStore.fetchPlan()
                .flatMap(plan ->
                        Flux.fromIterable(population)
                            .map(aid -> plan.fetch(aid).stream())
                                .reduce(Stream::concat)
                                    .map(Stream::toList)
                                        .filter(not(List::isEmpty))
                                            .flatMap(selectionStrategy::select)
                                                .retryWhen(fixedDelay(2, of(10, MILLIS)))
                                                    .map(selected -> selected.map(AdBudget::aid))
                );
    }
}
