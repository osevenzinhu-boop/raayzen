package com.nationswaw.model;

import java.util.UUID;

public class PendingConversion {
    public final UUID player;
    public final String fromId;
    public final String toId;
    public final double amount;
    public final long expiresAt;

    public PendingConversion(UUID player, String fromId, String toId, double amount, long timeoutMs) {
        this.player = player;
        this.fromId = fromId;
        this.toId = toId;
        this.amount = amount;
        this.expiresAt = System.currentTimeMillis() + timeoutMs;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
