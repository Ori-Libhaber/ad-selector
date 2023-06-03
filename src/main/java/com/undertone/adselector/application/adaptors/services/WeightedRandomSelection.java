package com.undertone.adselector.application.adaptors.services;

import com.undertone.adselector.application.ports.in.AdSelectionStrategy;
import com.undertone.adselector.application.ports.out.AdDistributionStore;
import com.undertone.adselector.application.ports.out.InfrastructureException;
import com.undertone.adselector.application.ports.out.InfrastructureException.StoreException.OperationFailedException;
import com.undertone.adselector.model.AdBudget;
import com.undertone.adselector.model.AdDistribution;
import com.undertone.adselector.model.Status;
import com.undertone.adselector.model.exceptions.ApplicationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toCollection;

@Component
public class WeightedRandomSelection implements AdSelectionStrategy {

    private final AdDistributionStore distributionStore;

    @Autowired
    public WeightedRandomSelection(AdDistributionStore distributionStore) {
        this.distributionStore =
                requireNonNull(distributionStore,
                    "Argument adDistributionStore must not be null");
    }

    @Override
    public Mono<Optional<AdBudget>> select(List<AdBudget> population) throws ApplicationException {
        return Mono.just(population)
                .flatMap(distributionStore::fetchDistributions)
                .map(distributions ->
                    distributions.stream().filter(not(AdDistribution::isExhausted))
                            .collect(toCollection(ArrayList::new)))
                .flatMap(candidates ->
                    Mono.fromCallable(() ->
                            RandomSelector.weighted(candidates, AdDistribution::priority)
                                    .next(new Random())
                    )
                )
                .flatMap(theOne ->
                        distributionStore.incrementDistribution(theOne)
                                .flatMap(status ->
                                        switch (status) {
                                            case FAILURE -> Mono.just(Optional.empty());
                                            case SUCCESS -> Mono.just(Optional.of(theOne));
                                            case CONFLICT ->
                                                    Mono.error(new OperationFailedException
                                                            (format("Unable to increment for: %s", theOne.aid())));
                                        }
                                )
                );
    }

}
