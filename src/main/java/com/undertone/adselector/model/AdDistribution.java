package com.undertone.adselector.model;

import java.util.Objects;

public interface AdDistribution extends AdBudget {

    public long remainingQuota();

    public default boolean isEmpty() {
        return Objects.equals(this, EMPTY);
    }

    public static final AdDistribution EMPTY = new AdDistribution() {
        @Override public String aid() { return "EMPTY"; }
        @Override public double priority() { return 0; }
        @Override public long quota() { return 0; }
        @Override public long remainingQuota() { return 0; }
        @Override public boolean isEmpty() { return true; }
    };

}
