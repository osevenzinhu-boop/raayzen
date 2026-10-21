package com.nationswaw.model;

import java.util.UUID;

public class PendingJoin {
    public final UUID player;
    public final String nationId;
    public final long expiresAt;

    public PendingJoin(UUID player, String nationId, long timeoutMs) {
        this.player = player;
        this.nationId = nationId;
        this.expiresAt = System.currentTimeMillis() + timeoutMs;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
