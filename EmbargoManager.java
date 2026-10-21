package com.nationswaw.systems;

import com.nationswaw.NationsWaW;
import com.nationswaw.gui.Theme;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embargo: +10%/dia ate +50% enquanto nação A aguarda confirmacao de guerra de B.
 * Key: targetNationId (embargada) -> percent increase
 */
public class EmbargoManager {
    private final NationsWaW plugin;
    // targetNation -> current multiplier increase (0.0 to 0.5)
    private final Map<String, Double> embargoes = new ConcurrentHashMap<>();
    // targetNation -> proposer nation
    private final Map<String, String> embargoBy = new ConcurrentHashMap<>();

    public EmbargoManager(NationsWaW plugin) {
        this.plugin = plugin;
        // daily tick
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickDaily, 20L * 60 * 60 * 24, 20L * 60 * 60 * 24);
    }

    public void applyEmbargo(String targetNationId, String byNationId) {
        embargoes.putIfAbsent(targetNationId.toLowerCase(), 0.10);
        embargoBy.put(targetNationId.toLowerCase(), byNationId.toLowerCase());
        notifyNation(targetNationId, "<red>Embargo economico aplicado! Precos de exportacao +10% (acumula ate +50%/dia).</red>");
    }

    public void removeEmbargo(String targetNationId) {
        embargoes.remove(targetNationId.toLowerCase());
        embargoBy.remove(targetNationId.toLowerCase());
        notifyNation(targetNationId, "<green>Embargo economico removido.</green>");
    }

    public double getEmbargoMultiplier(String nationId) {
        return 1.0 + embargoes.getOrDefault(nationId.toLowerCase(), 0.0);
    }

    public boolean hasEmbargo(String nationId) {
        return embargoes.containsKey(nationId.toLowerCase());
    }

    public double getEmbargoPercent(String nationId) {
        return embargoes.getOrDefault(nationId.toLowerCase(), 0.0) * 100.0;
    }

    private void tickDaily() {
        double max = 0.50;
        double step = 0.10;
        for (var e : embargoes.entrySet()) {
            double next = Math.min(max, e.getValue() + step);
            embargoes.put(e.getKey(), next);
            notifyNation(e.getKey(), "<red>Embargo aumentou para +" + String.format("%.0f", next * 100) + "% nas exportacoes.</red>");
        }
    }

    private void notifyNation(String nationId, String msg) {
        var n = plugin.getNationManager().getNation(nationId);
        if (n == null) return;
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
