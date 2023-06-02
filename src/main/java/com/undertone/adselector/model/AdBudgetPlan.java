package com.undertone.adselector.model;

import java.util.Objects;
import java.util.Optional;

public interface AdBudgetPlan {

    public Optional<AdBudget> fetch(String aid);

    public default boolean isEmpty() {
        return Objects.equals(this, EMPTY);
    }

    public static final AdBudgetPlan EMPTY = new AdBudgetPlan() {
        @Override public Optional<AdBudget> fetch(String aid) { return Optional.empty(); }
        @Override public boolean isEmpty() { return true; }
    };

}
