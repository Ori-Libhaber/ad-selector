package com.undertone.adselector.infrastructure.out;

import com.undertone.adselector.application.ports.out.AdDistributionStore;
import com.undertone.adselector.application.ports.out.InfrastructureException;
import com.undertone.adselector.model.AdBudget;
import com.undertone.adselector.model.AdDistribution;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

import static java.util.Objects.requireNonNull;

public class RedisBackedAdDistributionStore implements AdDistributionStore {

    private final ReactiveStringRedisTemplate redisTemplate;


    public RedisBackedAdDistributionStore(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = requireNonNull(redisTemplate, "Argument redisTemplate must not be null");
    }

    @Override
    public Mono<AdDistribution> fetchDistribution(AdBudget adBudget) throws InfrastructureException.StoreException {
        return null;
    }

    @Override
    public Flux<AdDistribution> fetchDistributions(Set<AdBudget> adBudgets) throws InfrastructureException.StoreException {
        return null;
    }

}
