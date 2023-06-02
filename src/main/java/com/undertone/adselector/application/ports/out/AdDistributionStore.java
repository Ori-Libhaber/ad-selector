package com.undertone.adselector.application.ports.out;

import com.undertone.adselector.model.AdBudget;
import com.undertone.adselector.model.AdDistribution;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

public interface AdDistributionStore {

    public Mono<AdDistribution> fetchDistribution(AdBudget adBudget) throws InfrastructureException.StoreException;

    public Flux<AdDistribution> fetchDistributions(Set<AdBudget> adBudgets) throws InfrastructureException.StoreException;


}
