package com.undertone.adselector.application.ports.out;

import com.undertone.adselector.model.AdBudgetPlan;
import reactor.core.publisher.Mono;

public interface AdBudgetPlanStore {

    public Mono<AdBudgetPlan> fetchPlan() throws InfrastructureException.StoreException;

}
