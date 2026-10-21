package com.nationswaw.utils;

import com.nationswaw.NationsWaW;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ConfigManager {
    private final NationsWaW plugin;
    private FileConfiguration config;
    private double spread, iof, minAmount, maxAmount, dailyLimit, minRate, maxRate, initialRate, warThreshold, startingBalance;
    private int votingDurationMinutes;
    private boolean pvpWarOnly;
    private long autoSaveIntervalTicks;
    private final Map<String, NationConfig> nationConfigs = new HashMap<>();

    public ConfigManager(NationsWaW plugin) { this.plugin = plugin; }

    public void load() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        // ISSP 3% / ISDN 2% (substituem spread/iof). Aceita chaves antigas como fallback.
        spread = config.getDouble("exchange.issp", config.getDouble("exchange.spread", 0.03));
        iof = config.getDouble("exchange.isdn", config.getDouble("exchange.iof", 0.02));
        minAmount = config.getDouble("exchange.min-amount", 1_000.0);
        maxAmount = config.getDouble("exchange.max-amount", 20_000_000.0);
        dailyLimit = config.getDouble("exchange.daily-limit-per-player", 0); // 0 = sem limite diario
        minRate = config.getDouble("exchange.min-rate", 0.01);
        maxRate = config.getDouble("exchange.max-rate", 100.0);
        initialRate = config.getDouble("exchange.initial-rate", 1.0);
        warThreshold = config.getDouble("war.approval-threshold", 0.75);
        votingDurationMinutes = config.getInt("war.voting-duration-minutes", 30);
        pvpWarOnly = config.getBoolean("pvp.war-only", true);
        startingBalance = config.getDouble("economy.starting-balance", 1000.0);
        autoSaveIntervalTicks = config.getInt("settings.auto-save-interval-minutes", 5) * 60L * 20L;

        nationConfigs.clear();
        ConfigurationSection sec = config.getConfigurationSection("nations");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection n = sec.getConfigurationSection(id);
                if (n == null) continue;
                NationConfig nc = new NationConfig();
                nc.displayName = n.getString("display-name", id);
                nc.currency = n.getString("currency", "Moeda");
                nc.symbol = n.getString("symbol", "$");
                nc.centralBank = n.getString("central-bank", "Banco Central");
                nc.color = n.getString("color", "#FFFFFF");
                nc.flagMaterial = n.getString("flag-material", "WHITE_BANNER");
                nc.initialTreasury = n.getDouble("initial-treasury", 2_500_000);
                nc.initialCentralBank = n.getDouble("initial-central-bank", 2_500_000);
                nationConfigs.put(id.toLowerCase(), nc);
            }
        }
    }

    public double getSpread() { return spread; }
    public double getIof() { return iof; }
    public double getIssp() { return spread; } // 3% Imposto sobre Salário Politico
    public double getIsdn() { return iof; }   // 2% Imposto sobre Desenvolvimento Nacional
    public double getMinAmount() { return minAmount; }
    public double getMaxAmount() { return maxAmount; }
    public double getDailyLimit() { return dailyLimit; }
    public double getMinRate() { return minRate; }
    public double getMaxRate() { return maxRate; }
    public double getInitialRate() { return initialRate; }
    public double getWarThreshold() { return warThreshold; }
    public int getVotingDurationMinutes() { return votingDurationMinutes; }
    public boolean isPvpWarOnly() { return pvpWarOnly; }
    public double getStartingBalance() { return startingBalance; }
    public long getAutoSaveIntervalTicks() { return autoSaveIntervalTicks; }
    public Set<String> getNationIds() { return nationConfigs.keySet(); }
    public NationConfig getNationConfig(String id) { return nationConfigs.get(id.toLowerCase()); }
    public String getMessage(String key) { return config.getString("messages." + key, key); }
    public String getPrefix() { return getMessage("prefix"); }

    public boolean isTreasuryImpactEnabled() { return config.getBoolean("treasury-impact.enabled", true); }
    public double getCriticalThreshold() { return config.getDouble("treasury-impact.critical-threshold", 1_000_000); }
    public double getHealthyThreshold() { return config.getDouble("treasury-impact.healthy-threshold", 5_000_000); }
    public double getDevaluationRate() { return config.getDouble("treasury-impact.devaluation-rate-daily", 0.01); }
    public double getAppreciationRate() { return config.getDouble("treasury-impact.appreciation-rate-daily", 0.01); }
    public double getMaxDailyVariation() { return config.getDouble("treasury-impact.max-daily-variation", 0.05); }

    public static class NationConfig {
        public String displayName, currency, symbol, centralBank, color, flagMaterial;
        public double initialTreasury;
        public double initialCentralBank;
    }
}
