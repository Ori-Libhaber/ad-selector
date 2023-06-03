package com.undertone.adselector.infrastructure.out;

import com.undertone.adselector.application.ports.out.AdDistributionStore;
import com.undertone.adselector.application.ports.out.InfrastructureException;
import com.undertone.adselector.application.ports.out.InfrastructureException.StoreException;
import com.undertone.adselector.application.ports.out.InfrastructureException.StoreException.OperationFailedException;
import com.undertone.adselector.model.AdBudget;
import com.undertone.adselector.model.AdDistribution;
import com.undertone.adselector.model.Status;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

import static com.undertone.adselector.model.Status.*;
import static java.util.Objects.*;
import static java.util.stream.Collectors.toUnmodifiableList;

@Slf4j
@Component
public class RedisBackedAdDistributionStore implements AdDistributionStore {

    private final ReactiveStringRedisTemplate redisTemplate;

    @Autowired
    public RedisBackedAdDistributionStore(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = requireNonNull(redisTemplate, "Argument redisTemplate must not be null");
    }

    @Override
    public Mono<AdDistribution> fetchDistribution(AdBudget adBudget) throws OperationFailedException {
        requireNonNull(adBudget, "Argument adBudget must not be null");

        return redisTemplate.opsForValue().get(adBudget.aid())
                .map(strRemainingQuota ->
                        new RedisBackedAdDistribution(adBudget,
                                convertToLongValue(strRemainingQuota)));
    }

    @Override
    public Mono<List<AdDistribution>> fetchDistributions(List<AdBudget> adBudgets) throws OperationFailedException {
        requireNonNull(adBudgets, "Argument adBudgets must not be null");

        List<String> orderedAids = mapToOrderedAids(adBudgets);

        return redisTemplate.opsForValue().multiGet(orderedAids)
                .map(orderedValues -> {

                    var result = new ArrayList<AdDistribution>(adBudgets.size());

                    var budgetIter = adBudgets.iterator();
                    var strValuesItr = orderedValues.iterator();

                    while (budgetIter.hasNext() && strValuesItr.hasNext()) {
                        long spentQuota = convertToLongValue(strValuesItr.next());
                        AdBudget adBudget = budgetIter.next();
                        result.add(new RedisBackedAdDistribution(adBudget, adBudget.quota() - spentQuota));
                    }
                    return result;
                });
    }

    @Override
    public Mono<Status> incrementDistribution(AdDistribution adDistribution) throws StoreException {
        requireNonNull(adDistribution, "Argument adDistribution must not be null");

        if(adDistribution.remainingQuota() > 0) {
            return redisTemplate.opsForValue().increment(adDistribution.aid())
                    .flatMap(updatedSpent -> {
                        if(updatedSpent > adDistribution.quota()) {
                            log.error("Detected overspending for: {}, rolling back", adDistribution.aid());
                            return redisTemplate.opsForValue()
                                    .decrement(adDistribution.aid())
                                        .thenReturn(CONFLICT);
                        }
                        return Mono.just(SUCCESS);
                    })
                    .onErrorReturn(FAILURE);
        }

        log.warn("Attempted to increment exhausted AdDistribution: {}", adDistribution);
        return Mono.just(FAILURE);
    }

    private static long convertToLongValue(String strLong) {
        if (nonNull(strLong)) {
            try {
                return Long.parseLong(strLong);
            } catch (NumberFormatException nfe) {
                log.error("Failed to parse long from: {} instantiating to 0", strLong, nfe);
            }
        }
        return 0L;
    }

    private static List<String> mapToOrderedAids(List<AdBudget> adBudgets) {
        return adBudgets.stream().map(AdBudget::aid).collect(toUnmodifiableList());
    }

    private record RedisBackedAdDistribution
            (@Delegate AdBudget delegate, long remainingQuota) implements AdDistribution { }

}
