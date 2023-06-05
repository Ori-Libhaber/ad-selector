package com.undertone.adselector.application.adaptors.services;

import com.undertone.adselector.application.ports.in.AdSelectionStrategy;

import com.undertone.adselector.application.ports.in.UseCaseException.AbortedException;
import com.undertone.adselector.application.ports.out.AdDistributionStore;

import com.undertone.adselector.model.AdBudget;
import com.undertone.adselector.model.AdDistribution;
import com.undertone.adselector.model.exceptions.ApplicationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

@Component
public class WeightedRandomSelection implements AdSelectionStrategy {

    private static final int MAXIMUM_PREFIX_SUM_VALUE = 9_900; // assumed for maximum of 100 candidates having priority values in range [0.01, 0.99]
    private final Iterator<Integer> randomIndexIterator =
            new Random().ints(0, MAXIMUM_PREFIX_SUM_VALUE).iterator();
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
                /**
                 * Filter out unprocessable entries
                 */
                .map(adBudgets -> adBudgets.stream()
                        .filter(this::isProcessableCandidate)
                            .collect(toCollection(ArrayList::new)))
                .filter(not(List::isEmpty))
                /**
                 * Filter out exhausted distributions
                 */
                .flatMap(distributionStore::fetchDistributions)
                .map(distributions ->
                    distributions.stream().filter(not(AdDistribution::isExhausted))
                            .collect(toCollection(ArrayList::new)))
                .filter(not(List::isEmpty))
                /**
                 * Perform random weighted selection
                 */
                .map(this::doSelect)
                /**
                 * Try to increment distribution count of selected
                 */
                .flatMap(theOne ->
                        distributionStore.incrementDistribution(theOne)
                                .flatMap(status ->
                                        switch (status) {
                                            case FAILURE -> Mono.just(Optional.empty());
                                            case SUCCESS -> Mono.just(Optional.of(theOne));
                                            case CONFLICT ->
                                                    Mono.error(new AbortedException
                                                            (format("Unable to increment for: %s", theOne.aid())));
                                        }
                                )
                );
    }

    /**
     * Avoiding costly floating point calculations resulting from using priority double values.
     * Instead, it is assumed that all priority values could be converted into plain decimal representation,
     * meaning, all values are expected to be in range of [0.01, 0.99].
     * Any non-zero values, smaller than 0.01 are treated as having priority value of 0.01.
     *
     * @param priority expected to be in range of [0.01, 0.99]
     * @return calculation result of (Double.max(priority, 0.01d) * 100d)
     */
    private long simplifyPriorityValue(double priority) {
        long valueSign = (Double.isNaN(priority) || priority <= 0.0) ? 0 : 1;
        return (long) (Double.max(priority, 0.01d) * 100d) * valueSign;
    }

    private boolean isProcessableCandidate(AdBudget adBudget) {
        return !(adBudget.isEmpty() ||
                    adBudget.quota() <= 0 ||
                        simplifyPriorityValue(adBudget.priority()) <= 0);
    }

    AdDistribution doSelect(final List<AdDistribution> candidates) {
        requireNonNull(candidates, "Argument candidates must not be null");

        if (!candidates.isEmpty()) {
            final long[] prefixSums = new long[candidates.size()];
            final int length = prefixSums.length;

            int index = 0;
            long totalSum = 0;
            for (AdDistribution ad : candidates) {
                prefixSums[index++] = (totalSum += ad.priority(this::simplifyPriorityValue));
            }

            if(totalSum > 0) {
                long randomSelection = randomIndexIterator.next() % totalSum;

                /**
                 * Using binary search to look for suitable index
                 */
                int low = 0, high = length - 1, mid = length/2;
                while (low <= high) {
                    if (prefixSums[mid] < randomSelection) {
                        low = mid + 1;
                    } else {
                        high = mid - 1;
                    }
                    mid = low + ((high - low) / 2);
                }

                return candidates.get(mid);
            }
        }

        return AdDistribution.EMPTY;
    }

}
