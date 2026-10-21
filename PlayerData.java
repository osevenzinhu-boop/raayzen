package com.nationswaw.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private String nationId;
    private RoleType role = RoleType.NONE;
    private final Map<String, Double> balances = new HashMap<>();
    private double dailyExchangeVolume;
    private long lastExchangeResetDay;

    public PlayerData(UUID uuid) { this.uuid = uuid; }

    public UUID getUuid() { return uuid; }
    public String getNationId() { return nationId; }
    public void setNationId(String v) { this.nationId = v; }
    public RoleType getRole() { return role; }
    public void setRole(RoleType v) { this.role = v; }
    public Map<String, Double> getBalances() { return balances; }

    public double getBalance(String currencyNationId) {
        return balances.getOrDefault(currencyNationId, 0.0);
    }
    public void setBalance(String currencyNationId, double amount) {
        balances.put(currencyNationId, Math.max(0, amount));
    }
    public void addBalance(String currencyNationId, double amount) {
        setBalance(currencyNationId, getBalance(currencyNationId) + amount);
    }
    public boolean removeBalance(String currencyNationId, double amount) {
        double current = getBalance(currencyNationId);
        if (current < amount) return false;
        setBalance(currencyNationId, current - amount);
        return true;
    }
    public double getDailyExchangeVolume() { return dailyExchangeVolume; }
    public void setDailyExchangeVolume(double v) { this.dailyExchangeVolume = v; }
    public long getLastExchangeResetDay() { return lastExchangeResetDay; }
    public void setLastExchangeResetDay(long v) { this.lastExchangeResetDay = v; }
    public boolean isImmigrant() { return nationId == null || role == RoleType.NONE; }
}
