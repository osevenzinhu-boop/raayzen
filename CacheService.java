package com.nationswaw.systems;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nationswaw.model.Nation;
import com.nationswaw.model.PlayerData;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;

/**
 * Caches Caffeine de alta performance — limita RAM e reduz lookups repetidos.
 */
public final class CacheService {

    private final Cache<UUID, PlayerData> playerDataCache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    private final Cache<UUID, String> playerNationIdCache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    private final Cache<String, Double> exchangeRateCache = Caffeine.newBuilder()
            .maximumSize(64)
            .expireAfterWrite(Duration.ofSeconds(30))
            .build();

    private final Cache<String, Integer> claimCountCache = Caffeine.newBuilder()
            .maximumSize(16)
            .expireAfterWrite(Duration.ofSeconds(10))
            .build();

    public PlayerData getPlayerData(UUID uuid, Function<UUID, PlayerData> loader) {
        return playerDataCache.get(uuid, loader);
    }

    public void putPlayerData(UUID uuid, PlayerData data) {
        if (data != null) playerDataCache.put(uuid, data);
    }

    public void invalidatePlayer(UUID uuid) {
        playerDataCache.invalidate(uuid);
        playerNationIdCache.invalidate(uuid);
    }

    public String getPlayerNationId(UUID uuid, Function<UUID, String> loader) {
        return playerNationIdCache.get(uuid, loader);
    }

    public void putPlayerNationId(UUID uuid, String nationId) {
        if (nationId == null) playerNationIdCache.invalidate(uuid);
        else playerNationIdCache.put(uuid, nationId);
    }

    public Double getExchangeRate(String fromToKey, Function<String, Double> loader) {
        return exchangeRateCache.get(fromToKey, loader);
    }

    public void invalidateExchangeRates() {
        exchangeRateCache.invalidateAll();
    }

    public int getClaimCount(String nationId, Function<String, Integer> loader) {
        Integer v = claimCountCache.get(nationId, loader);
        return v == null ? 0 : v;
    }

    public void invalidateClaims(String nationId) {
        if (nationId != null) claimCountCache.invalidate(nationId);
        else claimCountCache.invalidateAll();
    }

    public void invalidateAll() {
        playerDataCache.invalidateAll();
        playerNationIdCache.invalidateAll();
        exchangeRateCache.invalidateAll();
        claimCountCache.invalidateAll();
    }
}
