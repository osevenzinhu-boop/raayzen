package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.model.Nation;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Indice de valor baseado no SALDO TOTAL DA UNIAO (Tesouro + Banco Central).
 *
 * Estavel:  2.000.000 .. 10.000.000  -> 0%
 * Valoriza: acima de 10M -> +0.05% por cada 1M acima de 10M
 * Desvaloriza: abaixo de 2M -> curva quadratic a ate -95% em 0
 *
 * Taxa A->B = (1 + indiceA) / (1 + indiceB)
 */
public class TreasuryImpactManager {
    private final NationsWaW plugin;
    private static final double STABLE_MIN = 2_000_000.0;
    private static final double STABLE_MAX = 10_000_000.0;
    private static final double APPREC_PER_M = 0.0005; // 0.05% = 0.0005 em decimal
    private static final double MAX_DEVAL = 0.95; // 95%

    public TreasuryImpactManager(NationsWaW plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::recalculateAllRates, 20L * 60, 20L * 60 * 60);
    }

    /** Saldo Total da Uniao */
    public double getTotalBalance(String nationId) {
        Nation n = plugin.getNationManager().getNation(nationId);
        if (n == null) return 0;
        return n.getTreasury() + n.getCentralBankBalance();
    }

    /**
     * Indice decimal: 0.0 = estavel, 0.0025 = +0.25%, -0.2375 = -23.75%
     */
    public double getIndex(String nationId) {
        double total = getTotalBalance(nationId);
        if (total >= STABLE_MIN && total <= STABLE_MAX) {
            return 0.0;
        }
        if (total > STABLE_MAX) {
            // +0.05% por 1M acima de 10M
            return ((total - STABLE_MAX) / 1_000_000.0) * APPREC_PER_M;
        }
        // total < 2M: -95% * ((2M - total) / 2M)^2
        double ratio = (STABLE_MIN - total) / STABLE_MIN;
        if (ratio < 0) ratio = 0;
        if (ratio > 1) ratio = 1;
        return -MAX_DEVAL * (ratio * ratio);
    }

    public double getIndexPercent(String nationId) {
        return getIndex(nationId) * 100.0;
    }

    public double computeRate(String fromId, String toId) {
        double idxFrom = getIndex(fromId);
        double idxTo = getIndex(toId);
        double rate = (1.0 + idxFrom) / (1.0 + idxTo);
        double min = plugin.getConfigManager().getMinRate();
        double max = plugin.getConfigManager().getMaxRate();
        return Math.max(min, Math.min(max, rate));
    }

    public void recalculateAllRates() {
        var ids = plugin.getConfigManager().getNationIds();
        for (String from : ids) {
            for (String to : ids) {
                if (from.equalsIgnoreCase(to)) continue;
                plugin.getExchangeManager().setPairRate(from, to, computeRate(from, to));
            }
        }
        plugin.getExchangeManager().saveRates();
        plugin.getLogger().info("Taxas recalculadas (Saldo Total Uniao).");
    }

    public String getStatusEmoji(String nationId) {
        double pct = getIndexPercent(nationId);
        if (pct < -0.01) return "■";
        if (pct > 0.01) return "●";
        return "○";
    }

    public String getStatusLabel(String nationId) {
        double pct = getIndexPercent(nationId);
        if (pct < -0.01) return "Desvalorizada (" + String.format("%.2f", pct) + "%)";
        if (pct > 0.01) return "Valorizada (+" + String.format("%.2f", pct) + "%)";
        return "Estavel (0,00%)";
    }

    /** Cor MiniMessage para o status */
    public String getStatusColor(String nationId) {
        double pct = getIndexPercent(nationId);
        if (pct < -0.01) return "red";
        if (pct > 0.01) return "green";
        return "yellow";
    }

    public void alertNation(Nation n, String msg) {
        var c = MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getPrefix() + msg);
        for (var uuid : n.getCitizens()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(c);
                p.sendActionBar(c);
            }
        }
    }
}
