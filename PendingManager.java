package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.PendingConversion;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PendingManager {
    private final NationsWaW plugin;
    private final Map<UUID, PendingConversion> pending = new ConcurrentHashMap<>();

    public PendingManager(NationsWaW plugin) { this.plugin = plugin; }

    public void put(PendingConversion c) { pending.put(c.player, c); }
    public PendingConversion get(UUID uuid) {
        PendingConversion c = pending.get(uuid);
        if (c != null && c.isExpired()) {
            pending.remove(uuid);
            return null;
        }
        return c;
    }
    public PendingConversion remove(UUID uuid) { return pending.remove(uuid); }
}
