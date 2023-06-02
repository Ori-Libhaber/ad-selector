package com.undertone.adselector.model;

import com.undertone.adselector.application.ports.in.UseCaseException;

import java.util.Objects;
import java.util.function.Function;

import static java.lang.String.format;

public interface AdBudget {

    public String aid();

    public double priority();

    public default <N extends Number> N priority(Function<Double, N> numberTypeConverter) throws UseCaseException.AbortedException.TypeConversionException {
        final double priority = priority();
        try {
            return numberTypeConverter.apply(priority);
        } catch (Exception ex) {
            throw new UseCaseException.AbortedException.TypeConversionException(format("Failed to convert priority value: %d", priority), ex);
        }
    }

    public long quota();

    public default boolean isEmpty() {
        return Objects.equals(this, EMPTY);
    }

    public static final AdBudget EMPTY = new AdBudget() {
        @Override public String aid() { return "EMPTY"; }
        @Override public double priority() { return 0; }
        @Override public long quota() { return 0; }
        @Override public boolean isEmpty() { return true; }
    };

}
