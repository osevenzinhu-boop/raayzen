package com.nationswaw.model;

public class PendingAlliance {
    public final String fromNation;
    public final String toNation;
    public final long expiresAt;

    public PendingAlliance(String from, String to, long timeoutMs) {
        this.fromNation = from;
        this.toNation = to;
        this.expiresAt = System.currentTimeMillis() + timeoutMs;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
