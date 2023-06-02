package com.undertone.adselector.application.ports.in;

import com.undertone.adselector.model.AdBudget;
import com.undertone.adselector.model.exceptions.ApplicationException;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.Set;

public interface AdSelectionStrategy {

    public Mono<Optional<AdBudget>> select(Set<AdBudget> population) throws ApplicationException;

}
