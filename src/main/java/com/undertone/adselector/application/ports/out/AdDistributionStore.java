package com.undertone.adselector.application.ports.out;

import com.undertone.adselector.application.ports.out.InfrastructureException.StoreException;
import com.undertone.adselector.model.AdBudget;
import com.undertone.adselector.model.AdDistribution;
import com.undertone.adselector.model.Status;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

public interface AdDistributionStore {

    public Mono<AdDistribution> fetchDistribution(AdBudget adBudget) throws StoreException;

    public Mono<List<AdDistribution>> fetchDistributions(List<AdBudget> adBudgets) throws StoreException;

    public Mono<Status> incrementDistribution(AdDistribution adDistribution) throws StoreException;

}
