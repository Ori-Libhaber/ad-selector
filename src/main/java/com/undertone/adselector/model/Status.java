package com.undertone.adselector.model;

public enum Status {

    SUCCESS(true, false),
    FAILURE(false, false),
    CONFLICT(false, true);

    private final boolean retryable;

    private final boolean success;

    Status(boolean success, boolean retryable) {
        this.retryable = retryable;
        this.success = success;
    }
}
